package com.testforge.backend.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for every test that needs the real application and a real PostgreSQL.
 *
 * One container is started for the whole test run and shared by all subclasses, so the Spring context is
 * created only once. The Flyway migrations run against it exactly as they do in production, and
 * ddl-auto=validate checks that every entity matches the migrated schema.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    // Same major version as docker-compose.yml.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15")
            .withDatabaseName("testforge")
            .withUsername("testforge")
            .withPassword("testforge");

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata")); POSTGRES.start(); // stopped automatically when the JVM exits
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected JdbcTemplate jdbc;
    protected Db db;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.db = new Db(jdbc);
    }

    /** Every test starts from empty tables, with ids restarting at 1, and with cold caches. */
    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        SecurityContextHolder.clearContext();
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
