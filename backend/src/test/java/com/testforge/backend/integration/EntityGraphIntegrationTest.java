package com.testforge.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestStep;
import com.testforge.backend.repository.DefectRepository;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.repository.TestRunRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import com.testforge.backend.specification.DefectSpecifications;
import com.testforge.backend.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * These tests are deliberately NOT @Transactional. With open-in-view disabled, anything an @EntityGraph did
 * not load is an uninitialised proxy once the repository call returns, which is exactly how the services see it.
 */
class EntityGraphIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired TestRunRepository runRepository;
    @Autowired TestRunResultRepository resultRepository;
    @Autowired TestCaseRepository testCaseRepository;
    @Autowired DefectRepository defectRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    private long ownerId;
    private long assigneeId;
    private long projectId;
    private long suiteId;
    private long caseId;
    private long runId;
    private long resultId;
    private long defectId;

    private void seed() {
        ownerId = db.user("owner");
        assigneeId = db.user("assignee");
        projectId = db.project(ownerId, "P");
        suiteId = db.suite(projectId, "S");
        caseId = db.testCase(suiteId, "Login", "HIGH", "READY", "smoke");
        db.step(caseId, 2, "click", "opens");
        db.step(caseId, 3, "verify", "visible");
        db.step(caseId, 1, "type", "accepted");
        runId = db.run(projectId, suiteId, "Run", "COMPLETED", 1, ownerId);
        resultId = db.result(runId, caseId, "FAILED");
        defectId = db.defect(projectId, resultId, "Bug", "HIGH", "OPEN", ownerId);
        jdbc.update("update defects set assignee_id = ? where id = ?", assigneeId, defectId);
    }

    @Test
    void runWithDetailsLoadsProjectOwnerSuiteAndTriggererInOneGo() {
        seed();

        TestRun run = runRepository.findWithDetailsById(runId).orElseThrow();

        assertThat(Hibernate.isInitialized(run.getProject())).isTrue();
        assertThat(Hibernate.isInitialized(run.getProject().getOwner())).isTrue();
        assertThat(Hibernate.isInitialized(run.getSuite())).isTrue();
        assertThat(Hibernate.isInitialized(run.getTriggeredBy())).isTrue();
        assertThat(run.getProject().getOwner().getUsername()).isEqualTo("owner");
    }

    @Test
    void aPlainFindByIdLeavesTheAssociationsLazy() {
        seed();

        TestRun run = runRepository.findById(runId).orElseThrow();

        assertThat(Hibernate.isInitialized(run.getProject())).isFalse();
        assertThat(Hibernate.isInitialized(run.getSuite())).isFalse();
    }

    @Test
    void pagedRunsComeWithTheirProjectSuiteAndTriggerer() {
        seed();

        Page<TestRun> page = runRepository.findByProjectId(projectId, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(run -> {
            assertThat(Hibernate.isInitialized(run.getProject())).isTrue();
            assertThat(Hibernate.isInitialized(run.getSuite())).isTrue();
            assertThat(Hibernate.isInitialized(run.getTriggeredBy())).isTrue();
        });
    }

    @Test
    void resultsOfARunComeWithTheirTestCaseAndExecutor() {
        seed();

        List<TestRunResult> results = resultRepository.findByRunIdOrderByIdAsc(runId);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(Hibernate.isInitialized(result.getTestCase())).isTrue();
            assertThat(Hibernate.isInitialized(result.getExecutedBy())).isTrue(); // null counts as initialised
            assertThat(result.getTestCase().getTitle()).isEqualTo("Login");
        });
    }

    @Test
    void aResultWithContextReachesTheOwnerOfItsProject() {
        seed();

        TestRunResult result = resultRepository.findWithContextById(resultId).orElseThrow();

        assertThat(Hibernate.isInitialized(result.getRun())).isTrue();
        assertThat(Hibernate.isInitialized(result.getRun().getSuite())).isTrue();
        assertThat(Hibernate.isInitialized(result.getRun().getProject())).isTrue();
        assertThat(result.getRun().getProject().getOwner().getUsername()).isEqualTo("owner");
    }

    @Test
    void aTestCaseWithItsStepsComesBackInStepOrder() {
        seed();

        TestCase testCase = testCaseRepository.findWithStepsById(caseId).orElseThrow();

        assertThat(Hibernate.isInitialized(testCase.getSteps())).isTrue();
        assertThat(testCase.getSteps()).extracting(TestStep::getStepNumber).containsExactly(1, 2, 3);
        assertThat(testCase.getSteps()).extracting(TestStep::getAction).containsExactly("type", "click", "verify");
    }

    @Test
    void aPlainFindByIdDoesNotLoadTheSteps() {
        seed();

        TestCase testCase = testCaseRepository.findById(caseId).orElseThrow();

        assertThat(Hibernate.isInitialized(testCase.getSteps())).isFalse();
    }

    @Test
    void aDefectWithDetailsLoadsEverythingItsResponseNeeds() {
        seed();

        Defect defect = defectRepository.findWithDetailsById(defectId).orElseThrow();

        assertThat(Hibernate.isInitialized(defect.getProject().getOwner())).isTrue();
        assertThat(Hibernate.isInitialized(defect.getReporter())).isTrue();
        assertThat(Hibernate.isInitialized(defect.getAssignee())).isTrue();
        assertThat(Hibernate.isInitialized(defect.getTestResult())).isTrue();
        assertThat(Hibernate.isInitialized(defect.getTestResult().getRun())).isTrue();
        assertThat(Hibernate.isInitialized(defect.getTestResult().getTestCase())).isTrue();
        assertThat(defect.getAssignee().getUsername()).isEqualTo("assignee");
    }

    @Test
    void defectFiltersAreAppliedInTheDatabase() {
        seed();
        db.defect(projectId, null, "second", "LOW", "RESOLVED", ownerId);
        db.defect(projectId, null, "third", "HIGH", "RESOLVED", ownerId);
        long otherProject = db.project(db.user("other"), "Other");
        db.defect(otherProject, null, "elsewhere", "HIGH", "OPEN", ownerId);
        PageRequest firstPage = PageRequest.of(0, 10);

        assertThat(titles(defectRepository.findAll(DefectSpecifications.filter(projectId, null, null), firstPage)))
                .containsExactlyInAnyOrder("Bug", "second", "third");
        assertThat(titles(defectRepository.findAll(DefectSpecifications.filter(projectId, DefectStatus.RESOLVED, null), firstPage)))
                .containsExactlyInAnyOrder("second", "third");
        assertThat(titles(defectRepository.findAll(DefectSpecifications.filter(projectId, DefectStatus.RESOLVED, Severity.HIGH), firstPage)))
                .containsExactly("third");
        assertThat(titles(defectRepository.findAll(DefectSpecifications.filter(projectId, DefectStatus.CLOSED, null), firstPage)))
                .isEmpty();
    }

    @Test
    void defectPagesCountTheTotalAndSplitCorrectly() {
        seed();
        for (int i = 1; i <= 4; i++) {
            db.defect(projectId, null, "extra " + i, "LOW", "OPEN", ownerId);
        }

        Page<Defect> first = defectRepository.findAll(DefectSpecifications.filter(projectId, null, null), PageRequest.of(0, 2));
        Page<Defect> last = defectRepository.findAll(DefectSpecifications.filter(projectId, null, null), PageRequest.of(2, 2));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(last.getContent()).hasSize(1);
    }

    @Test
    void listingRunsDoesNotIssueOneQueryPerRun() {
        seed();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long withOneRun = statementsFor(statistics);

        for (int i = 0; i < 5; i++) {
            db.run(projectId, suiteId, "extra " + i, "COMPLETED", 2 + i, ownerId);
        }
        long withSixRuns = statementsFor(statistics);

        assertThat(withSixRuns).as("statements for 6 runs vs 1 run").isEqualTo(withOneRun);
    }

    private long statementsFor(Statistics statistics) {
        statistics.clear();
        List<TestRun> runs = runRepository.findByProjectId(projectId, PageRequest.of(0, 20)).getContent();
        runs.forEach(run -> {
            run.getProject().getName();
            run.getSuite().getName();
            if (run.getTriggeredBy() != null) {
                run.getTriggeredBy().getUsername();
            }
        });
        return statistics.getPrepareStatementCount();
    }

    private static List<String> titles(Page<Defect> page) {
        return page.getContent().stream().map(Defect::getTitle).toList();
    }
}
