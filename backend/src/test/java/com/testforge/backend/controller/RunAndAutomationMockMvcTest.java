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

import com.testforge.backend.automation.AutomationService;
import com.testforge.backend.automation.RunLogStore;
import com.testforge.backend.common.exception.GlobalExceptionHandler;
import com.testforge.backend.config.SecurityConfig;
import com.testforge.backend.dto.CreateRunRequest;
import com.testforge.backend.dto.RunDetailResponse;
import com.testforge.backend.dto.RunResponse;
import com.testforge.backend.dto.RunResultResponse;
import com.testforge.backend.dto.RunSummary;
import com.testforge.backend.dto.UpdateResultRequest;
import com.testforge.backend.model.Priority;
import com.testforge.backend.model.ResultStatus;
import com.testforge.backend.model.RunStatus;
import com.testforge.backend.model.RunType;
import com.testforge.backend.security.JwtService;
import com.testforge.backend.service.TestRunService;
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

@WebMvcTest(controllers = {TestRunController.class, AutomationController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@WithMockUser(username = "manisha")
class RunAndAutomationMockMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private TestRunService testRunService;
    @MockitoBean private AutomationService automationService;

    @Test
    void testRunEndpointsCreateListDetailUpdateResultAndAbort() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        RunSummary summary = new RunSummary(5L, 3L, 1L, 0L, 0L, 1L, 75.0);
        RunResponse run = new RunResponse(
                100L, 1L, 5L, "Smoke Suite", "Manual Smoke Run",
                RunType.MANUAL, RunStatus.RUNNING, now, null, "manisha", summary);
        RunResultResponse result = new RunResultResponse(
                501L, 20L, "Login with valid JWT", Priority.HIGH, ResultStatus.PASSED,
                120L, "ok", null, "manisha");
        RunDetailResponse detail = new RunDetailResponse(run, List.of(result));
        RunResponse aborted = new RunResponse(
                100L, 1L, 5L, "Smoke Suite", "Manual Smoke Run",
                RunType.MANUAL, RunStatus.ABORTED, now, now, "manisha", summary);

        when(testRunService.create(any(CreateRunRequest.class))).thenReturn(run);
        when(testRunService.list(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(run)));
        when(testRunService.get(100L)).thenReturn(detail);
        when(testRunService.updateResult(eq(100L), eq(501L), any(UpdateResultRequest.class)))
                .thenReturn(result);
        when(testRunService.abort(100L)).thenReturn(aborted);

        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "suiteId": 5,
                                  "name": "Manual Smoke Run"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/runs/100"))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/api/runs").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100));

        mockMvc.perform(get("/api/runs/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value(100))
                .andExpect(jsonPath("$.results[0].status").value("PASSED"));

        mockMvc.perform(patch("/api/runs/100/results/501")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PASSED",
                                  "durationMs": 120,
                                  "comment": "ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501));

        mockMvc.perform(post("/api/runs/100/abort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABORTED"));
    }

    @Test
    void automationTriggerAndLogEndpointsReturnAcceptedAndLogChunk() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        RunSummary summary = new RunSummary(5L, 0L, 0L, 0L, 0L, 5L, 0.0);
        RunResponse triggered = new RunResponse(
                202L, 1L, 5L, "API Suite", "Automated CI Run",
                RunType.AUTOMATED, RunStatus.QUEUED, now, null, "manisha", summary);

        when(automationService.trigger(any(CreateRunRequest.class))).thenReturn(triggered);
        when(automationService.log(202L, 0L))
                .thenReturn(new RunLogStore.Chunk(List.of("[INFO] Starting suite", "[PASS] Auth test"), 2L));

        mockMvc.perform(post("/api/runs/automated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "suiteId": 5,
                                  "name": "Automated CI Run"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/runs/202"))
                .andExpect(jsonPath("$.type").value("AUTOMATED"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/runs/202/log").param("from", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0]").value("[INFO] Starting suite"))
                .andExpect(jsonPath("$.next").value(2));
    }
}