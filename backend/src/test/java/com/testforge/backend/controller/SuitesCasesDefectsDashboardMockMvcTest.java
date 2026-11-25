package com.testforge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.common.exception.GlobalExceptionHandler;
import com.testforge.backend.config.SecurityConfig;
import com.testforge.backend.dto.ChangeDefectStatusRequest;
import com.testforge.backend.dto.CreateDefectRequest;
import com.testforge.backend.dto.DashboardDtos.Activity;
import com.testforge.backend.dto.DashboardDtos.FlakyTest;
import com.testforge.backend.dto.DashboardDtos.PassRatePoint;
import com.testforge.backend.dto.DashboardDtos.SeverityCount;
import com.testforge.backend.dto.DashboardDtos.Summary;
import com.testforge.backend.dto.DefectResponse;
import com.testforge.backend.dto.TestCaseRequest;
import com.testforge.backend.dto.TestCaseResponse;
import com.testforge.backend.dto.TestCaseSummaryResponse;
import com.testforge.backend.dto.TestSuiteRequest;
import com.testforge.backend.dto.TestSuiteResponse;
import com.testforge.backend.model.DefectStatus;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.Severity;
import com.testforge.backend.model.TestCaseStatus;
import com.testforge.backend.security.JwtService;
import com.testforge.backend.service.DashboardService;
import com.testforge.backend.service.DefectService;
import com.testforge.backend.service.TestCaseService;
import com.testforge.backend.service.TestSuiteService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    TestSuiteController.class,
    TestCaseController.class,
    DefectController.class,
    DashboardController.class
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(username = "manisha")
class SuitesCasesDefectsDashboardMockMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private TestSuiteService testSuiteService;
    @MockitoBean private TestCaseService testCaseService;
    @MockitoBean private DefectService defectService;
    @MockitoBean private DashboardService dashboardService;

    @Test
    void suiteAndCaseEndpointsWorkWithFiltersAndLocationHeaders() throws Exception {
        TestSuiteResponse suite = new TestSuiteResponse(5L, 1L, "Smoke Suite", "Critical smoke checks");
        when(testSuiteService.create(eq(1L), any(TestSuiteRequest.class))).thenReturn(suite);
        when(testSuiteService.listByProject(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(suite)));

        mockMvc.perform(post("/api/projects/1/suites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Smoke Suite",
                                  "description": "Critical smoke checks"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/1/suites/5"))
                .andExpect(jsonPath("$.name").value("Smoke Suite"));

        mockMvc.perform(get("/api/projects/1/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5));

        LocalDateTime now = LocalDateTime.now();
        TestCaseResponse createdCase = new TestCaseResponse(
                20L, 5L, "Login with valid JWT", "Verifies auth token", "User exists",
                Priority.HIGH, TestCaseStatus.READY, "auth,smoke", 1L, now, now, List.of());
        when(testCaseService.create(eq(5L), any(TestCaseRequest.class))).thenReturn(createdCase);

        mockMvc.perform(post("/api/suites/5/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Login with valid JWT",
                                  "description": "Verifies auth token",
                                  "preconditions": "User exists",
                                  "priority": "HIGH",
                                  "status": "READY",
                                  "tags": "auth,smoke",
                                  "steps": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/cases/20"))
                .andExpect(jsonPath("$.id").value(20));

        TestCaseSummaryResponse summaryCase = new TestCaseSummaryResponse(
                20L, 5L, "Login with valid JWT", Priority.HIGH, TestCaseStatus.READY, "auth,smoke", now);
        when(testCaseService.search(
                        eq(1L), eq(Priority.HIGH), eq(TestCaseStatus.READY), eq("JWT"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summaryCase)));

        mockMvc.perform(get("/api/cases")
                        .param("projectId", "1")
                        .param("priority", "HIGH")
                        .param("status", "READY")
                        .param("q", "JWT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Login with valid JWT"));
    }

    @Test
    void defectEndpointsCreateListAndTransitionStatus() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        DefectResponse defect = new DefectResponse(
                30L, 1L, 100L, 10L, "Regression Run", 20L, "Login with valid JWT",
                "Token expiry returns 500", "Expected 401", Severity.CRITICAL, DefectStatus.OPEN,
                1L, "manisha", 1L, "manisha", now, now, DefectStatus.OPEN.allowedTransitions());

        when(defectService.create(any(CreateDefectRequest.class))).thenReturn(defect);
        when(defectService.list(eq(1L), eq(DefectStatus.OPEN), eq(Severity.CRITICAL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(defect)));
        when(defectService.get(30L)).thenReturn(defect);

        DefectResponse inProgress = new DefectResponse(
                30L, 1L, 100L, 10L, "Regression Run", 20L, "Login with valid JWT",
                "Token expiry returns 500", "Expected 401", Severity.CRITICAL, DefectStatus.IN_PROGRESS,
                1L, "manisha", 1L, "manisha", now, now, DefectStatus.IN_PROGRESS.allowedTransitions());
        when(defectService.changeStatus(eq(30L), eq(DefectStatus.IN_PROGRESS))).thenReturn(inProgress);

        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "testResultId": 100,
                                  "title": "Token expiry returns 500",
                                  "description": "Expected 401",
                                  "severity": "CRITICAL",
                                  "assigneeId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/defects/30"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/defects")
                        .param("projectId", "1")
                        .param("status", "OPEN")
                        .param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(30));

        mockMvc.perform(get("/api/defects/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("CRITICAL"));

        mockMvc.perform(patch("/api/defects/30/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void dashboardEndpointsReturnMetricsAndLists() throws Exception {
        when(dashboardService.summary(1L)).thenReturn(new Summary(50L, 12L, 92.5, 3L));
        when(dashboardService.passRateTrend(1L, 30))
                .thenReturn(List.of(new PassRatePoint("2026-11-24", 18L, 20L, 90.0)));
        when(dashboardService.defectsBySeverity(1L))
                .thenReturn(List.of(new SeverityCount(Severity.HIGH, 2L)));
        when(dashboardService.recentActivity(1L, 15))
                .thenReturn(List.of(new Activity("RUN", 10L, "Nightly Run", "Passed 18/20", "2026-11-25T10:00:00Z")));
        when(dashboardService.flakyTests(1L, 10))
                .thenReturn(List.of(new FlakyTest(20L, "Login test", 10L, 2L, 3L, 0.3)));

        mockMvc.perform(get("/api/dashboard/summary").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(50))
                .andExpect(jsonPath("$.passRate").value(92.5));

        mockMvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passed").value(18));

        mockMvc.perform(get("/api/dashboard/defects-by-severity").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("HIGH"));

        mockMvc.perform(get("/api/dashboard/recent-activity").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RUN"));

        mockMvc.perform(get("/api/dashboard/flaky-tests").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flakiness").value(0.3));
    }
}