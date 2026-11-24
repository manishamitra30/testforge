package com.testforge.backend.support;

import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;

/** Small builders for entities, so each test reads as a story instead of a wall of setters. */
public final class TestData {

    private TestData() {}

    public static User user(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .password("not-a-real-hash")
                .build();
    }

    public static Project project(Long id, User owner) {
        Project project = new Project();
        project.setId(id);
        project.setName("Project " + id);
        project.setOwner(owner);
        return project;
    }

    public static TestSuite suite(Long id, Project project) {
        TestSuite suite = new TestSuite();
        suite.setId(id);
        suite.setProject(project);
        suite.setName("Suite " + id);
        return suite;
    }

    public static TestCase testCase(Long id, TestSuite suite, String title, Priority priority) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setSuite(suite);
        testCase.setTitle(title);
        testCase.setPriority(priority);
        return testCase;
    }

    public static TestRun run(Long id, TestSuite suite, RunStatus status) {
        TestRun run = new TestRun();
        run.setId(id);
        run.setProject(suite.getProject());
        run.setSuite(suite);
        run.setName("Run " + id);
        run.setStatus(status);
        return run;
    }

    public static TestRunResult result(Long id, TestRun run, TestCase testCase, ResultStatus status) {
        TestRunResult result = new TestRunResult();
        result.setId(id);
        result.setRun(run);
        result.setTestCase(testCase);
        result.setStatus(status);
        return result;
    }

    public static Defect defect(Long id, Project project, User reporter, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setProject(project);
        defect.setReporter(reporter);
        defect.setTitle("Defect " + id);
        defect.setStatus(status);
        return defect;
    }
}