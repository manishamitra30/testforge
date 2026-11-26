package com.testforge.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.testforge.backend.support.AbstractApiTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** One QA story through the real HTTP API: project, suite, cases, run, results, defect, dashboard, cleanup. */
class EndToEndWorkflowApiTest extends AbstractApiTest {

    private long id(MvcResult result) throws Exception {
        return ((Number) read(result, "$.id")).longValue();
    }

    @Test
    void aFullTestingCycleWorksThroughTheApi() throws Exception {
        String token = register("qauser");

        long projectId = createProject(token, "Shop");
        long suiteId = id(mvc.perform(as(token, json(post("/api/projects/" + projectId + "/suites"), "{\"name\":\"Checkout\"}")))
                .andExpect(status().isCreated()).andReturn());

        String steps = "[{\"stepNumber\":1,\"action\":\"add item\",\"expectedResult\":\"in cart\"},"
                + "{\"stepNumber\":2,\"action\":\"pay\",\"expectedResult\":\"order placed\"}]";
        MvcResult created = mvc.perform(as(token, json(post("/api/suites/" + suiteId + "/cases"),
                        "{\"title\":\"Pay by card\",\"priority\":\"HIGH\",\"status\":\"READY\",\"steps\":" + steps + "}")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn();
        long caseOne = id(created);
        mvc.perform(as(token, json(post("/api/suites/" + suiteId + "/cases"), "{\"title\":\"Pay by UPI\",\"status\":\"READY\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));

        MvcResult run = mvc.perform(as(token, json(post("/api/runs"), "{\"suiteId\":" + suiteId + ",\"name\":\"Smoke\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();
        long runId = id(run);

        MvcResult detail = mvc.perform(as(token, get("/api/runs/" + runId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andReturn();
        List<Number> resultIds = JsonPath.read(detail.getResponse().getContentAsString(), "$.results[*].id");

        mvc.perform(as(token, json(patch("/api/runs/" + runId + "/results/" + resultIds.get(0).longValue()),
                        "{\"status\":\"PASSED\",\"durationMs\":120}")))
                .andExpect(status().isOk());
        mvc.perform(as(token, json(patch("/api/runs/" + runId + "/results/" + resultIds.get(1).longValue()),
                        "{\"status\":\"FAILED\",\"comment\":\"payment timed out\"}")))
                .andExpect(status().isOk());

        mvc.perform(as(token, get("/api/runs/" + runId))).andExpect(jsonPath("$.run.status").value("COMPLETED"));

        long failedResult = resultIds.get(1).longValue();
        long defectId = id(mvc.perform(as(token, json(post("/api/defects"),
                        "{\"testResultId\":" + failedResult + ",\"severity\":\"HIGH\"}")))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(as(token, json(post("/api/defects"), "{\"testResultId\":" + failedResult + "}")))
                .andExpect(status().isBadRequest());

        mvc.perform(as(token, json(patch("/api/defects/" + defectId + "/status"), "{\"status\":\"RESOLVED\"}")))
                .andExpect(status().isBadRequest()); // OPEN cannot jump straight to RESOLVED
        mvc.perform(as(token, json(patch("/api/defects/" + defectId + "/status"), "{\"status\":\"IN_PROGRESS\"}")))
                .andExpect(status().isOk());
        mvc.perform(as(token, json(patch("/api/defects/" + defectId + "/status"), "{\"status\":\"RESOLVED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(as(token, get("/api/defects?projectId=" + projectId + "&status=RESOLVED")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(token, get("/api/defects?projectId=" + projectId + "&status=OPEN")))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(as(token, get("/api/dashboard/summary?projectId=" + projectId))).andExpect(status().isOk());

        mvc.perform(as(token, delete("/api/projects/" + projectId))).andExpect(status().isNoContent());
        mvc.perform(as(token, get("/api/projects/" + projectId))).andExpect(status().isNotFound());
        for (String table : List.of("test_suites", "test_cases", "test_runs", "test_run_results", "defects")) {
            assertThat(db.count(table)).as(table).isZero();
        }
    }

    @Test
    void aFinishedRunCannotBeAborted() throws Exception {
        String token = register("qauser");
        long projectId = createProject(token, "P");
        long suiteId = db.suite(projectId, "S");
        long caseId = db.testCase(suiteId, "C", "HIGH", "READY", null);
        long userId = jdbc.queryForObject("select id from users where username='qauser'", Long.class);
        long runId = db.run(projectId, suiteId, "Done", "COMPLETED", 1, userId);
        db.result(runId, caseId, "PASSED");

        mvc.perform(as(token, post("/api/runs/" + runId + "/abort"))).andExpect(status().isBadRequest());
    }
}
