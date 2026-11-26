package com.testforge.backend.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.testforge.backend.support.AbstractApiTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class AuthApiTest extends AbstractApiTest {

    @Value("${app.jwt.secret}") String secret;

    private static String credentials(String user, String password) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}";
    }

    private static String registration(String user, String email, String password) {
        return "{\"username\":\"" + user + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Test
    void registeringConfirmsWithoutRevealingAPasswordOrToken() throws Exception {
        mvc.perform(json(post("/api/auth/register"), registration("manisha", "m@example.com", "secret123")))
                .andExpect(status().is2xxSuccessful())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully!"))
                .andExpect(jsonPath("$", not(hasKey("token"))))
                .andExpect(jsonPath("$", not(hasKey("password"))));
    }

    @Test
    void thePasswordIsStoredHashedNotAsPlainText() throws Exception {
        mvc.perform(json(post("/api/auth/register"), registration("manisha", "m@example.com", "secret123")))
                .andExpect(status().is2xxSuccessful());

        String stored = jdbc.queryForObject("select password from users where username = 'manisha'", String.class);
        org.assertj.core.api.Assertions.assertThat(stored).isNotEqualTo("secret123").startsWith("$2");
    }

    @Test
    void aTakenUsernameOrEmailIsRejected() throws Exception {
        register("manisha");

        mvc.perform(json(post("/api/auth/register"), registration("manisha", "new@example.com", "secret123")))
                .andExpect(status().is4xxClientError());
        mvc.perform(json(post("/api/auth/register"), registration("someoneelse", "manisha@example.com", "secret123")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loggingInWithTheRightPasswordReturnsAToken() throws Exception {
        register("manisha");

        mvc.perform(json(post("/api/auth/login"), credentials("manisha", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void aWrongPasswordAndAnUnknownUserAreBothRefused() throws Exception {
        register("manisha");

        mvc.perform(json(post("/api/auth/login"), credentials("manisha", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/login"), credentials("ghost", "secret123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromLoginOpensProtectedEndpoints() throws Exception {
        String token = register("manisha");

        mvc.perform(as(token, get("/api/projects"))).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRefuseRequestsWithoutAToken() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void aGarbageTokenIsRefused() throws Exception {
        mvc.perform(as("not.a.jwt", get("/api/projects"))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTamperedTokenIsRefused() throws Exception {
        String token = register("manisha");
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");

        mvc.perform(as(tampered, get("/api/projects"))).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        register("manisha");
        String expired = Jwts.builder().subject("manisha")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(key()).compact();

        mvc.perform(as(expired, get("/api/projects"))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
        register("manisha");
        SecretKey other = Keys.hmacShaKeyFor("another-secret-key-another-secret-key-12345".getBytes());
        String forged = Jwts.builder().subject("manisha")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(other).compact();

        mvc.perform(as(forged, get("/api/projects"))).andExpect(status().isUnauthorized());
    }

    @Test
    void theAuthorizationHeaderMustUseTheBearerScheme() throws Exception {
        String token = register("manisha");

        mvc.perform(get("/api/projects").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndOpenApiDocsStayPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/projects")).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void aValidClientRequestIdIsEchoedBackButAnUnsafeOneIsReplaced() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "my-trace-12345"))
                .andExpect(header().string("X-Request-Id", "my-trace-12345"));
        mvc.perform(get("/actuator/health").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", not("bad id with spaces")));
    }
}
