package com.learning.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
public class FallbackController {

    @GetMapping("/fallback")
    public ResponseEntity<Map<String, Object>> getFallback() {
        log.warn("Fallback triggered for GET request");
        return buildFallbackResponse();
    }

    @PostMapping("/fallback")
    public ResponseEntity<Map<String, Object>> postFallback() {
        log.warn("Fallback triggered for POST request");
        return buildFallbackResponse();
    }

    private ResponseEntity<Map<String, Object>> buildFallbackResponse() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service Unavailable",
                        "message", "The requested service is temporarily unavailable. Please try again later.",
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }
}
