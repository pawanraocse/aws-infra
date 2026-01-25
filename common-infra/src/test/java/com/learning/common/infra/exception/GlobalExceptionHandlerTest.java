package com.learning.common.infra.exception;

import com.learning.common.error.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("maps IllegalArgumentException to 400 with INVALID_REQUEST code")
    void mapsIllegalArgument() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "req-123");
        IllegalArgumentException ex = new IllegalArgumentException("Bad input");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Bad input");
        assertThat(response.getBody().requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("maps NotFoundException to 404 with NOT_FOUND code")
    void mapsNotFound() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        NotFoundException ex = new NotFoundException("User not found");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("User not found");
    }

    @Test
    @DisplayName("maps Exception to 500 with INTERNAL_ERROR code")
    void mapsGeneralException() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).contains("An unexpected error occurred");
    }
}
