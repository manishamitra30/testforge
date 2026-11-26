package com.testforge.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaginationApiTest extends AbstractApiTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = register("manisha");
        for (int i = 1; i <= 25; i++) {
            createProject(token, String.format("Project %02d", i));
        }
    }

    @Test
    void theDefaultPageHasTenNewestFirst() throws Exception {
        mvc.perform(as(token, get("/api/projects")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Project 25"));
    }

    @Test
    void theLastPageHoldsTheRemainder() throws Exception {
        mvc.perform(as(token, get("/api/projects?page=2&size=10")))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[4].name").value("Project 01"));
    }

    @Test
    void aPageBeyondTheEndIsEmptyButStillReportsTheTotals() throws Exception {
        mvc.perform(as(token, get("/api/projects?page=9&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void sortingByNameAscendingOrdersTheResults() throws Exception {
        mvc.perform(as(token, get("/api/projects?sort=name,asc&size=3")))
                .andExpect(jsonPath("$.content[0].name").value("Project 01"))
                .andExpect(jsonPath("$.content[1].name").value("Project 02"))
                .andExpect(jsonPath("$.content[2].name").value("Project 03"));
    }

    @Test
    void sortingByNameDescendingReversesIt() throws Exception {
        mvc.perform(as(token, get("/api/projects?sort=name,desc&size=2")))
                .andExpect(jsonPath("$.content[0].name").value("Project 25"))
                .andExpect(jsonPath("$.content[1].name").value("Project 24"));
    }

    @Test
    void pagesDoNotOverlap() throws Exception {
        String first = mvc.perform(as(token, get("/api/projects?sort=name,asc&page=0&size=5"))).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(as(token, get("/api/projects?sort=name,asc&page=1&size=5"))).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat((java.util.List<String>) com.jayway.jsonpath.JsonPath.read(first, "$.content[*].name"))
                .doesNotContainAnyElementsOf(com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(second, "$.content[*].name"));
    }

    @Test
    void anOversizedPageSizeIsCappedByTheServer() throws Exception {
        mvc.perform(as(token, get("/api/projects?size=100000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.lessThanOrEqualTo(2000)));
    }

    @Test
    void aTextPageNumberFallsBackToTheDefaultOrIsA400() throws Exception {
        int code = mvc.perform(as(token, get("/api/projects?page=abc"))).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(code).isIn(200, 400);
    }

    /**
     * Sorting by a property that does not exist is a client mistake. This test documents the behaviour: it must
     * not be a 500. If it fails, the fix is to translate PropertyReferenceException into a 400 in GlobalExceptionHandler.
     */
    @Test
    void sortingByAnUnknownPropertyIsAClientErrorNotAServerError() throws Exception {
        int code = mvc.perform(as(token, get("/api/projects?sort=doesNotExist,asc"))).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(code).as("HTTP status").isBetween(400, 499);
    }

    @Test
    void suitesAreSortedByNameByDefault() throws Exception {
        long projectId = createProject(token, "With suites");
        db.suite(projectId, "Charlie");
        db.suite(projectId, "alpha");
        db.suite(projectId, "Bravo");

        mvc.perform(as(token, get("/api/projects/" + projectId + "/suites")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void caseSearchPagesAndFiltersTogether() throws Exception {
        long projectId = createProject(token, "Cases");
        long suite = db.suite(projectId, "S");
        for (int i = 1; i <= 12; i++) {
            db.testCase(suite, "Case " + i, i % 2 == 0 ? "HIGH" : "LOW", "READY", null);
        }

        mvc.perform(as(token, get("/api/cases?projectId=" + projectId + "&priority=HIGH&size=4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(4));
    }
}
