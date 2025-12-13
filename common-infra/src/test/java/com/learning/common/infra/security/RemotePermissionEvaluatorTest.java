package com.learning.common.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RemotePermissionEvaluator.
 * Tests cover:
 * - Successful permission checks (granted/denied)
 * - HTTP error handling (fail closed)
 * - Null response handling
 */
@ExtendWith(MockitoExtension.class)
class RemotePermissionEvaluatorTest {

    @Mock
    private WebClient authWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private RemotePermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RemotePermissionEvaluator(authWebClient);
        setupWebClientMocks();
    }

    @SuppressWarnings("unchecked")
    private void setupWebClientMocks() {
        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void hasPermission_WhenAuthServiceReturnsTrue_ReturnsTrue() {
        // Given
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(true));

        // When
        boolean result = evaluator.hasPermission("user-123", "entry", "read");

        // Then
        assertTrue(result);
        verify(authWebClient).post();
    }

    @Test
    void hasPermission_WhenAuthServiceReturnsFalse_ReturnsFalse() {
        // Given
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(false));

        // When
        boolean result = evaluator.hasPermission("user-123", "entry", "write");

        // Then
        assertFalse(result);
    }

    @Test
    void hasPermission_WhenAuthServiceReturnsNull_ReturnsFalse() {
        // Given - auth-service returns null (unexpected)
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.empty());

        // When
        boolean result = evaluator.hasPermission("user-123", "entry", "delete");

        // Then - fail closed: deny access
        assertFalse(result);
    }

    @Test
    void hasPermission_WhenHttpError_ReturnsFalse() {
        // Given - auth-service returns 500
        WebClientResponseException serverError = WebClientResponseException.create(
                500, "Internal Server Error", null, null, StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.error(serverError));

        // When
        boolean result = evaluator.hasPermission("user-456", "entry", "read");

        // Then - fail closed: deny access on error
        assertFalse(result);
    }

    @Test
    void hasPermission_WhenConnectionError_ReturnsFalse() {
        // Given - network error (auth-service unreachable)
        when(responseSpec.bodyToMono(Boolean.class))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        // When
        boolean result = evaluator.hasPermission("user-789", "tenant", "manage");

        // Then - fail closed: deny access on error
        assertFalse(result);
    }

    @Test
    void hasPermission_SendsCorrectPayload() {
        // Given
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(true));

        // When
        evaluator.hasPermission("test-user", "resource-type", "action-name");

        // Then - verify correct URI called
        verify(requestBodyUriSpec).uri("/api/v1/permissions/check");
        verify(requestBodySpec).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void hasPermission_WhenUnauthorized401_ReturnsFalse() {
        // Given - auth-service returns 401 (token invalid)
        WebClientResponseException unauthorized = WebClientResponseException.create(
                401, "Unauthorized", null, null, StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.error(unauthorized));

        // When
        boolean result = evaluator.hasPermission("invalid-user", "entry", "read");

        // Then - fail closed
        assertFalse(result);
    }

    @Test
    void hasPermission_WhenForbidden403_ReturnsFalse() {
        // Given - auth-service returns 403
        WebClientResponseException forbidden = WebClientResponseException.create(
                403, "Forbidden", null, null, StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.error(forbidden));

        // When
        boolean result = evaluator.hasPermission("blocked-user", "admin", "access");

        // Then - fail closed
        assertFalse(result);
    }
}
