package com.learning.authservice.exception;

import com.learning.common.error.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler mapping (NT-08/NT-09).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("maps AuthLoginException INVALID_CREDENTIALS to 401 JSON")
    void mapsLoginInvalidCredentials() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "req-123");
        AuthLoginException ex = new AuthLoginException("INVALID_CREDENTIALS", "Invalid username or password");
        ResponseEntity<ErrorResponse> response = handler.handleAuthLogin(ex, req);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("maps AuthSignupException USER_EXISTS to 409 JSON")
    void mapsSignupUserExists() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "req-456");
        AuthSignupException ex = new AuthSignupException("USER_EXISTS", "User already exists");
        ResponseEntity<ErrorResponse> response = handler.handleAuthSignup(ex, req);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("USER_EXISTS");
    }

    @Test
    @DisplayName("maps RuntimeException to 500 JSON INTERNAL_ERROR")
    void mapsRuntimeException() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "req-789");
        RuntimeException ex = new RuntimeException("Boom");
        ResponseEntity<ErrorResponse> response = handler.handleRuntime(ex, req);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).contains("Boom");
    }
}
