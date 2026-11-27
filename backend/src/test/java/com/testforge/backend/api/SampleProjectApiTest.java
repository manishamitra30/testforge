package com.testforge.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.support.AbstractApiTest;
import org.junit.jupiter.api.Test;

class SampleProjectApiTest extends AbstractApiTest {

    @Test
    void createsTheSampleProjectOnceAndThenReturnsTheSameOne() throws Exception {
        String token = register("newcomer");

        mvc.perform(as(token, post("/api/sample-project")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));

        mvc.perform(as(token, post("/api/sample-project")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        mvc.perform(as(token, get("/api/projects")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Sample: ShopEasy Web"));
    }

    @Test
    void eachUserGetsTheirOwnSampleProject() throws Exception {
        String first = register("first");
        String second = register("second");

        mvc.perform(as(first, post("/api/sample-project"))).andExpect(status().isCreated());
        mvc.perform(as(second, post("/api/sample-project"))).andExpect(status().isCreated());

        mvc.perform(as(second, get("/api/projects")))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void requiresAnAuthenticatedUser() throws Exception {
        mvc.perform(post("/api/sample-project")).andExpect(status().is4xxClientError());
    }
}