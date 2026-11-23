package com.testforge.backend.automation;

import com.testforge.backend.model.ResultStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CucumberReportParser {

    private static final Pattern CASE_TAG = Pattern.compile("^@TC-(\\d{1,18})$");
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private record ScenarioResult(ResultStatus status, long durationMs, String error) {
    }

    public Map<Long, CaseOutcome> parse(Path file) {
        CucumberReport.Feature[] features;
        try {
            features = mapper.readValue(file.toFile(), CucumberReport.Feature[].class);
        } catch (RuntimeException ex) {
            throw new AutomationException("Could not read cucumber.json: " + ex.getMessage());
        }
        if (features == null) {
            return Map.of();
        }

        Map<Long, Accumulator> byCase = new LinkedHashMap<>();
        for (CucumberReport.Feature feature : features) {
            for (CucumberReport.Element element : orEmpty(feature.elements())) {
                if (!"scenario".equals(element.type())) {
                    continue;
                }
                Set<Long> caseIds = caseIds(orEmpty(feature.tags()), orEmpty(element.tags()));
                if (caseIds.isEmpty()) {
                    continue;
                }
                ScenarioResult scenario = evaluate(element);
                for (Long id : caseIds) {
                    byCase.computeIfAbsent(id, key -> new Accumulator()).add(scenario);
                }
            }
        }

        Map<Long, CaseOutcome> outcomes = new LinkedHashMap<>();
        byCase.forEach((id, accumulator) -> outcomes.put(id, accumulator.toOutcome()));
        return outcomes;
    }

    private ScenarioResult evaluate(CucumberReport.Element element) {
        List<CucumberReport.Step> all = new ArrayList<>();
        all.addAll(orEmpty(element.before()));
        all.addAll(orEmpty(element.steps()));
        all.addAll(orEmpty(element.after()));

        long nanos = 0;
        boolean passed = false;
        boolean failed = false;
        String error = null;

        for (CucumberReport.Step step : all) {
            CucumberReport.Result result = step.result();
            if (result == null || result.status() == null) {
                continue;
            }
            if (result.duration() != null) {
                nanos += result.duration();
            }
            String status = result.status().toLowerCase(Locale.ROOT);
            switch (status) {
                case "passed" -> passed = true;
                case "failed", "ambiguous" -> {
                    failed = true;
                    if (error == null) {
                        error = summarise(result.errorMessage(), "Step " + status);
                    }
                }
                case "undefined", "pending" -> {
                    failed = true;
                    if (error == null) {
                        error = "Step is " + status + (step.name() != null ? ": " + step.name() : "");
                    }
                }
                default -> {
                }
            }
        }

        ResultStatus status = failed ? ResultStatus.FAILED : passed ? ResultStatus.PASSED : ResultStatus.SKIPPED;
        return new ScenarioResult(status, Math.round(nanos / 1_000_000.0), error);
    }

    private static Set<Long> caseIds(List<CucumberReport.Tag> featureTags, List<CucumberReport.Tag> elementTags) {
        Set<Long> ids = new LinkedHashSet<>();
        List<CucumberReport.Tag> all = new ArrayList<>(featureTags);
        all.addAll(elementTags);
        for (CucumberReport.Tag tag : all) {
            if (tag.name() == null) {
                continue;
            }
            Matcher matcher = CASE_TAG.matcher(tag.name());
            if (matcher.matches()) {
                ids.add(Long.parseLong(matcher.group(1)));
            }
        }
        return ids;
    }

    private static String summarise(String text, String fallback) {
        String value = text == null || text.isBlank() ? fallback : text.strip();
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) + "..." : value;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static final class Accumulator {
        private boolean failed;
        private boolean passed;
        private long durationMs;
        private String error;

        void add(ScenarioResult scenario) {
            durationMs += scenario.durationMs();
            switch (scenario.status()) {
                case FAILED -> {
                    failed = true;
                    if (error == null) {
                        error = scenario.error();
                    }
                }
                case PASSED -> passed = true;
                default -> {
                }
            }
        }

        CaseOutcome toOutcome() {
            ResultStatus status = failed ? ResultStatus.FAILED : passed ? ResultStatus.PASSED : ResultStatus.SKIPPED;
            return new CaseOutcome(status, durationMs, failed ? error : null);
        }
    }
}