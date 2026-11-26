package com.testforge.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("testforge_test")
                    .withUsername("testforge")
                    .withPassword("testforge");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret",
                () -> "dGVzdGZvcmdlLWludGVncmF0aW9uLXRlc3Qtc2VjcmV0LWtleS0xMjM0NTY3ODkw");
        registry.add("app.jwt.expiration-ms", () -> "3600000");
        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl() + "&options=-c%20TimeZone=Asia/Kolkata");
    }
}