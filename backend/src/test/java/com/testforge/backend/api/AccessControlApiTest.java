package com.testforge.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.support.AbstractApiTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * TestForge has one access rule: only the owner of a project can see or change anything inside it.
 * (There are no ADMIN / QA_LEAD / TESTER permissions in the code, so the roles tests below prove that
 * claiming one grants nothing.)
 */
class AccessControlApiTest extends AbstractApiTest {

    @Value("${app.jwt.secret}") String secret;

    private String owner;
    private String intruder;
    private long projectId;
    private long suiteId;
    private long caseId;
    private long runId;
    private long resultId;
    private long defectId;

    @BeforeEach
    void setUp() throws Exception {
        owner = register("owner");
        intruder = register("intruder");
        projectId = createProject(owner, "Private");
        long ownerId = jdbc.queryForObject("select id from users where username='owner'", Long.class);
        suiteId = db.suite(projectId, "S");
        caseId = db.testCase(suiteId, "Case", "HIGH", "READY", null);
        runId = db.run(projectId, suiteId, "Run", "IN_PROGRESS", 0, ownerId);
        resultId = db.result(runId, caseId, "FAILED");
        defectId = db.defect(projectId, resultId, "Bug", "HIGH", "OPEN", ownerId);
    }

    @Test
    void theOwnerCanReadTheirProject() throws Exception {
        mvc.perform(as(owner, get("/api/projects/" + projectId))).andExpect(status().isOk());
    }

    @Test
    void someoneElseCannotReadUpdateOrDeleteTheProject() throws Exception {
        mvc.perform(as(intruder, get("/api/projects/" + projectId))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(put("/api/projects/" + projectId), "{\"name\":\"Hacked\"}"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, delete("/api/projects/" + projectId))).andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select name from projects where id = ?", String.class, projectId))
                .isEqualTo("Private");
    }

    @Test
    void theProjectListOnlyContainsYourOwnProjects() throws Exception {
        createProject(intruder, "Theirs");

        mvc.perform(as(owner, get("/api/projects")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Private"));
        mvc.perform(as(intruder, get("/api/projects")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Theirs"));
    }

    @Test
    void someoneElseCannotListOrAddSuites() throws Exception {
        mvc.perform(as(intruder, get("/api/projects/" + projectId + "/suites"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(post("/api/projects/" + projectId + "/suites"), "{\"name\":\"X\"}"))).andExpect(status().isForbidden());
    }

    @Test
    void someoneElseCannotAddOrSearchTestCases() throws Exception {
        mvc.perform(as(intruder, json(post("/api/suites/" + suiteId + "/cases"), "{\"title\":\"X\"}"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, get("/api/cases?projectId=" + projectId))).andExpect(status().isForbidden());
    }

    @Test
    void someoneElseCannotSeeOrTouchRuns() throws Exception {
        mvc.perform(as(intruder, get("/api/runs?projectId=" + projectId))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, get("/api/runs/" + runId))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(post("/api/runs"), "{\"suiteId\":" + suiteId + ",\"name\":\"X\"}"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/runs/" + runId + "/results/" + resultId), "{\"status\":\"PASSED\"}"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, post("/api/runs/" + runId + "/abort"))).andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status from test_run_results where id = ?", String.class, resultId))
                .isEqualTo("FAILED");
    }

    @Test
    void someoneElseCannotSeeOrChangeDefects() throws Exception {
        mvc.perform(as(intruder, get("/api/defects/" + defectId))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, get("/api/defects?projectId=" + projectId))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/defects/" + defectId + "/status"), "{\"status\":\"CLOSED\"}"))).andExpect(status().isForbidden());
        mvc.perform(as(intruder, json(post("/api/defects"), "{\"testResultId\":" + resultId + "}"))).andExpect(status().isForbidden());
    }

    @Test
    void someoneElseCannotReadTheDashboard() throws Exception {
        mvc.perform(as(intruder, get("/api/dashboard/summary?projectId=" + projectId))).andExpect(status().isForbidden());
        mvc.perform(as(owner, get("/api/dashboard/summary?projectId=" + projectId))).andExpect(status().isOk());
    }

    @Test
    void forbiddenResponsesUseTheStandardErrorShape() throws Exception {
        mvc.perform(as(intruder, get("/api/projects/" + projectId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/projects/" + projectId));
    }

    @Test
    void aValidTokenForADeletedUserNoLongerWorks() throws Exception {
        jdbc.update("delete from users where username = 'intruder'");

        int code = mvc.perform(as(intruder, get("/api/projects"))).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(code).isBetween(400, 499);
    }

    @Test
    void claimingTheAdminRoleInASignedTokenGrantsNothing() throws Exception {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        String adminClaim = Jwts.builder().subject("intruder").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000)).signWith(key).compact();

        mvc.perform(as(adminClaim, get("/api/projects/" + projectId))).andExpect(status().isForbidden());
    }
}
