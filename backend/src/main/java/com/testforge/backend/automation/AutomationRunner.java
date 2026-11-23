package com.testforge.backend.automation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRunner {

    private final AutomationProperties properties;
    private final AutomationRunRecorder recorder;
    private final CucumberReportParser reportParser;
    private final RunLogStore logStore;

    @Async("automationExecutor")
    public void execute(Long runId) {
        Path workspace = null;
        try {
            Optional<AutomationRunRecorder.RunPlan> plan = recorder.startRun(runId);
            if (plan.isEmpty()) {
                log.warn("Automation run {} is not queued any more, skipping", runId);
                return;
            }
            logStore.append(runId, "Run started with " + plan.get().caseIds().size() + " test case(s)");

            workspace = prepareWorkspace(runId, resolveSuiteDir(), plan.get().caseIds());
            int exitCode = runMaven(runId, workspace);
            if (exitCode != 0) {
                throw new AutomationException("Maven exited with code " + exitCode + ". See the run log for details");
            }

            Path report = workspace.resolve("target").resolve("cucumber.json");
            if (!Files.isRegularFile(report)) {
                throw new AutomationException("The suite finished but produced no cucumber.json report");
            }
            saveReportCopy(runId, report);

            Map<Long, CaseOutcome> outcomes = reportParser.parse(report);
            recorder.completeWithReport(runId, outcomes);
            logStore.append(runId, "Run completed: " + outcomes.size() + " test case(s) reported");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Automation run {} was interrupted", runId);
        } catch (AutomationException ex) {
            fail(runId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Automation run {} crashed", runId, ex);
            fail(runId, "Unexpected error: " + ex.getMessage());
        } finally {
            deleteRecursively(workspace);
        }
    }

    private void fail(Long runId, String reason) {
        logStore.append(runId, "Run failed: " + reason);
        try {
            recorder.markFailed(runId, reason);
        } catch (RuntimeException ex) {
            log.error("Could not mark run {} as failed", runId, ex);
        }
    }

    // The command is a constant. Nothing from a request or from the database is added to it.
    private List<String> buildCommand() {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.addAll(List.of("cmd.exe", "/c", "mvnw.cmd"));
        } else {
            command.addAll(List.of("sh", "./mvnw"));
        }
        command.addAll(List.of("-B", "-ntp", "test", "-Dmaven.test.failure.ignore=true"));
        return List.copyOf(command);
    }

    private int runMaven(Long runId, Path workspace) throws IOException, InterruptedException {
        Path wrapper = workspace.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        if (!Files.isRegularFile(wrapper)) {
            throw new AutomationException("The automation suite has no Maven wrapper (" + wrapper.getFileName() + ")");
        }

        ProcessBuilder builder = new ProcessBuilder(buildCommand());
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("TESTFORGE_HEADLESS", String.valueOf(properties.headless()));

        Path logFile = logsDir().resolve("run-" + runId + ".log");
        Files.createDirectories(logFile.getParent());

        logStore.append(runId, "Starting: " + String.join(" ", builder.command()));
        Process process = builder.start();
        Thread pump = new Thread(() -> pumpOutput(runId, process, logFile), "automation-output-" + runId);
        pump.setDaemon(true);
        pump.start();

        try {
            boolean finished = process.waitFor(properties.timeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                throw new AutomationException(
                        "The suite did not finish within " + properties.timeoutMinutes() + " minute(s) and was stopped");
            }
            pump.join(5_000);
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                killTree(process);
            }
        }
    }

    private void pumpOutput(Long runId, Process process, Path logFile) {
        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String line;
            while ((line = reader.readLine()) != null) {
                logStore.append(runId, line);
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ex) {
            log.debug("Output stream of run {} closed: {}", runId, ex.getMessage());
        }
    }

    private void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private Path prepareWorkspace(Long runId, Path suiteDir, List<Long> caseIds) throws IOException {
        Path workspace = workRoot().resolve("run-" + runId);
        if (workspace.startsWith(suiteDir)) {
            throw new AutomationException("The work directory must not be inside the automation suite");
        }
        deleteRecursively(workspace);
        copySuite(suiteDir, workspace);

        // Case IDs are numeric database ids, so this expression cannot carry anything else.
        String expression = caseIds.stream().map(id -> "@TC-" + id).collect(Collectors.joining(" or "));
        Path properties = workspace.resolve("src").resolve("test").resolve("resources")
                .resolve("junit-platform.properties");
        Files.createDirectories(properties.getParent());
        Files.writeString(properties,
                System.lineSeparator() + "cucumber.filter.tags=" + expression + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        logStore.append(runId, "Tag filter: " + expression);
        return workspace;
    }

    private void copySuite(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(source) && (name.equals("target") || name.equals(".git"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void saveReportCopy(Long runId, Path report) {
        try {
            Path logs = logsDir();
            Files.createDirectories(logs);
            Files.copy(report, logs.resolve("run-" + runId + "-cucumber.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.warn("Could not keep a copy of the report for run {}: {}", runId, ex.getMessage());
        }
    }

    private Path resolveSuiteDir() {
        Path dir = Path.of(properties.suiteDir()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(dir.resolve("pom.xml"))) {
            throw new AutomationException(
                    "Automation suite not found at " + dir + ". Set app.automation.suite-dir");
        }
        return dir;
    }

    private Path workRoot() {
        String configured = properties.workDir();
        Path root = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "testforge-runs")
                : Path.of(configured);
        return root.toAbsolutePath().normalize();
    }

    private Path logsDir() {
        return workRoot().resolve("logs");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ex) {
                    log.warn("Could not delete {}: {}", entry, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Could not clean up {}: {}", path, ex.getMessage());
        }
    }
}