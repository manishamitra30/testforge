package com.testforge.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.testforge.backend.model.Defect;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestCase;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.model.TestRun;
import com.testforge.backend.model.TestRunResult;
import com.testforge.backend.model.TestStep;
import com.testforge.backend.model.TestSuite;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.DefectRepository;
import com.testforge.backend.repository.ProjectRepository;
import com.testforge.backend.repository.TestCaseRepository;
import com.testforge.backend.repository.TestRunRepository;
import com.testforge.backend.repository.TestRunResultRepository;
import com.testforge.backend.repository.TestSuiteRepository;

import lombok.RequiredArgsConstructor;

/** Creates a realistic sample project (suites, cases with steps, a finished run and defects) for the current user. */
@Service
@RequiredArgsConstructor
public class SampleDataService {

    public static final String SAMPLE_PROJECT_NAME = "Sample: ShopEasy Web";

    public record SampleProject(Long projectId, boolean created) {}

    private final CurrentUserService currentUserService;
    private final ProjectRepository projectRepository;
    private final TestSuiteRepository suiteRepository;
    private final TestCaseRepository caseRepository;
    private final TestRunRepository runRepository;
    private final TestRunResultRepository resultRepository;
    private final DefectRepository defectRepository;

    /** Idempotent: returns the existing sample project if the user already has one. */
    @Transactional
    public SampleProject createSampleProject() {
        User user = currentUserService.getCurrentUser();

        var existing = projectRepository.findByOwnerId(user.getId(), Pageable.unpaged()).getContent().stream()
                .filter(p -> SAMPLE_PROJECT_NAME.equals(p.getName()))
                .findFirst();
        if (existing.isPresent()) {
            return new SampleProject(existing.get().getId(), false);
        }

        Project project = new Project();
        project.setName(SAMPLE_PROJECT_NAME);
        project.setDescription("An e-commerce web app with login, product search and checkout flows. "
                + "Explore the suites, the finished test run and the defects raised from failures.");
        project.setOwner(user);
        project = projectRepository.save(project);

        TestSuite auth = suite(project, "Authentication", "Sign up, login and session handling");
        TestSuite search = suite(project, "Search and catalog", "Finding and filtering products");
        TestSuite checkout = suite(project, "Checkout", "Cart, payment and order confirmation");

        List<TestCase> authCases = List.of(
                testCase(auth, user, "Login with valid credentials", Priority.CRITICAL, TestCaseStatus.READY,
                        "smoke,auth", "A registered user exists",
                        step("Open the login page", "The login form is displayed"),
                        step("Enter a valid username and password", "Fields accept the input"),
                        step("Click Sign in", "The user lands on the home page and sees their name")),
                testCase(auth, user, "Login with a wrong password shows an error", Priority.HIGH, TestCaseStatus.READY,
                        "auth,negative", "A registered user exists",
                        step("Enter a valid username and a wrong password", "Fields accept the input"),
                        step("Click Sign in", "An 'Invalid username or password' message appears and no session starts")),
                testCase(auth, user, "Logout ends the session", Priority.MEDIUM, TestCaseStatus.READY,
                        "auth", "User is logged in",
                        step("Click the profile menu", "The menu opens"),
                        step("Click Logout", "The user returns to the login page"),
                        step("Press the browser Back button", "Protected pages are not shown again")));

        List<TestCase> searchCases = List.of(
                testCase(search, user, "Search by product name", Priority.HIGH, TestCaseStatus.READY,
                        "smoke,search", null,
                        step("Type 'backpack' in the search box", "Suggestions appear"),
                        step("Press Enter", "Only products matching 'backpack' are listed")),
                testCase(search, user, "Filter products by price range", Priority.MEDIUM, TestCaseStatus.READY,
                        "search,filter", "Search results are visible",
                        step("Set the price filter to 500-1000", "The list refreshes"),
                        step("Check each product price", "Every price is within the chosen range")),
                testCase(search, user, "Sort products by rating", Priority.LOW, TestCaseStatus.DRAFT,
                        "search", null,
                        step("Choose 'Top rated' in the sort menu", "Products are reordered from highest to lowest rating")));

        List<TestCase> checkoutCases = List.of(
                testCase(checkout, user, "Add a product to the cart", Priority.CRITICAL, TestCaseStatus.READY,
                        "smoke,cart", "User is logged in",
                        step("Open a product page and click Add to cart", "The cart badge shows 1"),
                        step("Open the cart", "The product, quantity and total price are correct")),
                testCase(checkout, user, "Pay with a valid card", Priority.CRITICAL, TestCaseStatus.READY,
                        "payment,smoke", "Cart contains one product",
                        step("Go to checkout and enter shipping details", "Details are accepted"),
                        step("Enter a valid test card and pay", "An order confirmation page is shown"),
                        step("Check the confirmation email", "An email with the order number arrives")),
                testCase(checkout, user, "Pay with an expired card is rejected", Priority.HIGH, TestCaseStatus.READY,
                        "payment,negative", "Cart contains one product",
                        step("Enter a card with a past expiry date", "The form accepts the input"),
                        step("Click Pay", "A 'Card expired' error is shown and no order is created")),
                testCase(checkout, user, "Apply a discount code", Priority.MEDIUM, TestCaseStatus.READY,
                        "cart,promo", "Cart contains one product",
                        step("Enter code SAVE10 and apply it", "The total is reduced by 10 percent")),
                testCase(checkout, user, "Order history lists the new order", Priority.LOW, TestCaseStatus.READY,
                        "orders", "User has just placed an order",
                        step("Open My orders", "The latest order appears at the top with the right total")));

        TestRun authRun = finishedRun(project, auth, user, "Auth regression, build 41");
        result(authRun, authCases.get(0), ResultStatus.PASSED, 2300, null, user);
        result(authRun, authCases.get(1), ResultStatus.PASSED, 1800, null, user);
        result(authRun, authCases.get(2), ResultStatus.PASSED, 2100, null, user);

        TestRun checkoutRun = finishedRun(project, checkout, user, "Checkout regression, build 41");
        result(checkoutRun, checkoutCases.get(0), ResultStatus.PASSED, 3100, null, user);
        result(checkoutRun, checkoutCases.get(1), ResultStatus.PASSED, 5400, null, user);
        TestRunResult expired = result(checkoutRun, checkoutCases.get(2), ResultStatus.FAILED, 4200,
                "Expected 'Card expired' but the order was created", user);
        TestRunResult promo = result(checkoutRun, checkoutCases.get(3), ResultStatus.FAILED, 2600,
                "Total was 100.00, expected 90.00", user);
        result(checkoutRun, checkoutCases.get(4), ResultStatus.BLOCKED, 0,
                "Blocked by the card validation defect", user);

        TestRun searchRun = finishedRun(project, search, user, "Search smoke, build 41");
        result(searchRun, searchCases.get(0), ResultStatus.PASSED, 1500, null, user);
        TestRunResult filter = result(searchRun, searchCases.get(1), ResultStatus.FAILED, 2200,
                "Product priced 1,250 shown in the 500-1000 range", user);
        result(searchRun, searchCases.get(2), ResultStatus.SKIPPED, 0, "Case is still a draft", user);

        defect(project, user, expired, "Expired card is accepted at checkout", Severity.CRITICAL,
                DefectStatus.OPEN, "Entering a card with a past expiry date creates an order. "
                        + "Expected a validation error and no order.");
        defect(project, user, promo, "Discount code SAVE10 does not change the total", Severity.HIGH,
                DefectStatus.IN_PROGRESS, "After applying SAVE10 the cart total stays at 100.00.");
        defect(project, user, filter, "Price filter includes products above the maximum", Severity.MEDIUM,
                DefectStatus.RESOLVED, "Upper bound is exclusive in the query; fixed in build 42.");

        return new SampleProject(project.getId(), true);
    }

    private TestSuite suite(Project project, String name, String description) {
        TestSuite suite = new TestSuite();
        suite.setProject(project);
        suite.setName(name);
        suite.setDescription(description);
        return suiteRepository.save(suite);
    }

    private record StepSpec(String action, String expected) {}

    private static StepSpec step(String action, String expected) {
        return new StepSpec(action, expected);
    }

    private TestCase testCase(TestSuite suite, User user, String title, Priority priority, TestCaseStatus status,
            String tags, String preconditions, StepSpec... steps) {
        TestCase testCase = new TestCase();
        testCase.setSuite(suite);
        testCase.setTitle(title);
        testCase.setDescription(title + ".");
        testCase.setPreconditions(preconditions);
        testCase.setPriority(priority);
        testCase.setStatus(status);
        testCase.setTags(tags);
        testCase.setCreatedBy(user);
        List<TestStep> built = new ArrayList<>();
        int number = 1;
        for (StepSpec spec : steps) {
            TestStep step = new TestStep();
            step.setStepNumber(number++);
            step.setAction(spec.action());
            step.setExpectedResult(spec.expected());
            built.add(step);
        }
        testCase.replaceSteps(built);
        return caseRepository.save(testCase);
    }

    private TestRun finishedRun(Project project, TestSuite suite, User user, String name) {
        TestRun run = new TestRun();
        run.setProject(project);
        run.setSuite(suite);
        run.setName(name);
        run.setType(RunType.MANUAL);
        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(LocalDateTime.now());
        run.setTriggeredBy(user);
        return runRepository.save(run);
    }

    private TestRunResult result(TestRun run, TestCase testCase, ResultStatus status, long durationMs,
            String error, User user) {
        TestRunResult result = new TestRunResult();
        result.setRun(run);
        result.setTestCase(testCase);
        result.setStatus(status);
        result.setDurationMs(durationMs);
        result.setErrorMessage(error);
        result.setExecutedBy(user);
        return resultRepository.save(result);
    }

    private void defect(Project project, User user, TestRunResult result, String title, Severity severity,
            DefectStatus status, String description) {
        Defect defect = new Defect();
        defect.setProject(project);
        defect.setTestResult(result);
        defect.setTitle(title);
        defect.setDescription(description);
        defect.setSeverity(severity);
        defect.setStatus(status);
        defect.setReporter(user);
        defect.setAssignee(status == DefectStatus.OPEN ? null : user);
        defectRepository.save(defect);
    }
}