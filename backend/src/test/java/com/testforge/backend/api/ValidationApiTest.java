package com.testforge.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationApiTest extends AbstractApiTest {

    private String token;
    private long projectId;
    private long suiteId;

    @BeforeEach
    void setUp() throws Exception {
        token = register("manisha");
        projectId = createProject(token, "P");
        suiteId = db.suite(projectId, "S");
    }

    @Test
    void registrationReportsEveryInvalidFieldTogether() throws Exception {
        mvc.perform(json(post("/api/auth/register"), "{\"username\":\"ab\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void aBlankProjectNameIsRejectedWithAFieldError() throws Exception {
        mvc.perform(as(token, json(post("/api/projects"), "{\"name\":\"   \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void aTooLongProjectNameIsRejected() throws Exception {
        mvc.perform(as(token, json(post("/api/projects"), "{\"name\":\"" + "x".repeat(151) + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void updatingAProjectValidatesToo() throws Exception {
        mvc.perform(as(token, json(put("/api/projects/" + projectId), "{\"name\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void malformedJsonGivesAReadable400() throws Exception {
        mvc.perform(as(token, json(post("/api/projects"), "{\"name\": ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid JSON request body"));
    }

    @Test
    void anEmptyBodyIsA400NotA500() throws Exception {
        mvc.perform(as(token, post("/api/projects").contentType("application/json")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNonJsonContentTypeIsA415() throws Exception {
        mvc.perform(as(token, post("/api/projects").contentType("text/plain").content("name=P")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void aTextIdInThePathIsA400() throws Exception {
        mvc.perform(as(token, get("/api/projects/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void aMissingRequiredQueryParameterIsA400() throws Exception {
        mvc.perform(as(token, get("/api/runs")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'projectId' is missing"));
    }

    @Test
    void anUnknownEnumValueInTheQueryIsA400() throws Exception {
        mvc.perform(as(token, get("/api/cases?projectId=" + projectId + "&priority=URGENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTestCaseNeedsATitle() throws Exception {
        mvc.perform(as(token, json(post("/api/suites/" + suiteId + "/cases"), "{\"title\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void nestedStepFieldsAreValidatedWithTheirPath() throws Exception {
        String body = "{\"title\":\"T\",\"steps\":[{\"stepNumber\":0,\"action\":\"\",\"expectedResult\":\"\"}]}";

        mvc.perform(as(token, json(post("/api/suites/" + suiteId + "/cases"), body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['steps[0].action']").exists())
                .andExpect(jsonPath("$.fieldErrors['steps[0].expectedResult']").exists())
                .andExpect(jsonPath("$.fieldErrors['steps[0].stepNumber']").exists());
    }

    @Test
    void aRunNeedsASuiteAndAName() throws Exception {
        mvc.perform(as(token, json(post("/api/runs"), "{\"name\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.suiteId").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void aResultNeedsAStatusAndANonNegativeDuration() throws Exception {
        mvc.perform(as(token, json(patch("/api/runs/1/results/1"), "{\"durationMs\":-5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists())
                .andExpect(jsonPath("$.fieldErrors.durationMs").exists());
    }

    @Test
    void aDefectNeedsATestResult() throws Exception {
        mvc.perform(as(token, json(post("/api/defects"), "{\"title\":\"Bug\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.testResultId").exists());
    }

    @Test
    void aDefectStatusMustBeOneOfTheKnownValues() throws Exception {
        mvc.perform(as(token, json(patch("/api/defects/1/status"), "{\"status\":\"DONE\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingResourcesAreA404WithTheStandardErrorShape() throws Exception {
        mvc.perform(as(token, get("/api/projects/99999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/projects/99999"));
    }

    @Test
    void anUnsupportedMethodIsA405() throws Exception {
        mvc.perform(as(token, patch("/api/projects")))
                .andExpect(status().isMethodNotAllowed());
    }
}
