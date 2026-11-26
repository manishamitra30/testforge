package com.testforge.backend.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.testforge.backend.logging.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Full stack API tests: the real filter chain (request id, JWT, security), controllers, services and PostgreSQL.
 * JSON is written as plain strings and read with JsonPath, so the tests check the real wire format.
 */
public abstract class AbstractApiTest extends AbstractPostgresIntegrationTest {

    protected MockMvc mvc;

    @Autowired WebApplicationContext context;
    @Autowired RequestIdFilter requestIdFilter;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    /** Registers through the real endpoint and returns the JWT. */
    protected String register(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"secret123\"}";
        MvcResult result = mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/register"), body)).andReturn();
        MvcResult login = mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login"), "{\"username\":\"" + username + "\",\"password\":\"secret123\"}")).andReturn(); return read(login, "$.token");
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    protected long createProject(String token, String name) throws Exception {
        MvcResult result = mvc.perform(as(token, json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/projects"), "{\"name\":\"" + name + "\"}"))).andReturn();
        return ((Number) read(result, "$.id")).longValue();
    }
}
