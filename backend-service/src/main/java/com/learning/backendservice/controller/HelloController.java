package com.learning.backendservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;

@RestController
public class HelloController {

    @Operation(summary = "Hello API", description = "Returns a simple hello message.")
    @ApiResponse(responseCode = "200", description = "Hello response", content = @Content(schema = @Schema(implementation = String.class)))
    @GetMapping("/api/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello, world!");
    }
}
