package com.learning.awsinfra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.awsinfra.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSpecIntegrationTest extends AbstractIntegrationTest {
    private static final String OPENAPI_PATH = "/v3/api-docs";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiSpecIsAccessible() throws Exception {
        String url = "http://localhost:" + port + OPENAPI_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode())
                .as("OpenAPI endpoint should return 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .as("OpenAPI endpoint should return application/json")
                .isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/json");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());

        assertThat(root.has("openapi"))
                .as("OpenAPI spec should contain 'openapi' field")
                .isTrue();
        assertThat(root.has("paths"))
                .as("OpenAPI spec should contain 'paths' field")
                .isTrue();
        assertThat(root.has("components"))
                .as("OpenAPI spec should contain 'components' field")
                .isTrue();
    }
}
