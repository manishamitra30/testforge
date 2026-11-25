package com.testforge.backend.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testforge.backend.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiExportTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void exportsOpenApiSpecFromLiveEndpoint() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<String> response = client.get()
                .uri("/v3/api-docs")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();

        JsonNode spec = mapper.readTree(response.getBody());
        assertThat(spec.path("info").path("title").asText()).isEqualTo("TestForge API");
        assertThat(spec.path("paths").has("/api/auth/login")).isTrue();
        assertThat(spec.path("paths").has("/api/projects")).isTrue();

        Path output = Path.of("target", "openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writeValueAsString(spec));
        assertThat(Files.exists(output)).isTrue();
    }
}