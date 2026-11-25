package com.testforge.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityClassesTest {

    // 48-byte key encoded in Base64 for HS384
    private static final String BASE64_SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtServiceRejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtService("   ", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void jwtServiceGeneratesAndParsesTokenWithRole() {
        JwtService jwtService = new JwtService(BASE64_SECRET, 3600000L);

        String token = jwtService.generateToken("manisha", "ADMIN");
        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("manisha");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void jwtServiceRejectsExpiredToken() {
        JwtService jwtService = new JwtService(BASE64_SECRET, 3600000L);
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));
        Date past = new Date(System.currentTimeMillis() - 60_000L);

        String expiredToken = Jwts.builder()
                .subject("manisha")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.parse(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void filterSetsAuthenticationForValidBearerTokenAndDefaultsRoleToUser() throws Exception {
        JwtService jwtService = new JwtService(BASE64_SECRET, 3600000L);
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));
        String tokenWithoutRole = Jwts.builder()
                .subject("tester")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(key)
                .compact();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutRole);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("tester");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void filterClearsContextOnInvalidTokenAndContinuesChain() throws Exception {
        JwtService jwtService = new JwtService(BASE64_SECRET, 3600000L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterSkipsWsEndpoints() throws Exception {
        JwtService jwtService = new JwtService(BASE64_SECRET, 3600000L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

        MockHttpServletRequest wsRequest = new MockHttpServletRequest("GET", "/ws/runs/1");
        assertThat(filter.shouldNotFilter(wsRequest)).isTrue();

        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/runs/1");
        assertThat(filter.shouldNotFilter(apiRequest)).isFalse();
    }

    @Test
    void securityErrorWriterFormatsJsonAndEscapesPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter buffer = new StringWriter();

        when(request.getRequestURI()).thenReturn("/api/test\"path\\1");
        when(response.getWriter()).thenReturn(new PrintWriter(buffer));

        SecurityErrorWriter.write(request, response, 401, "Unauthorized", "Token expired");

        verify(response).setStatus(401);
        verify(response).setContentType("application/json");
        String json = buffer.toString();
        assertThat(json)
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"")
                .contains("\"message\":\"Token expired\"")
                .contains("\"path\":\"/api/test\\\"path\\\\1\"");
    }
}