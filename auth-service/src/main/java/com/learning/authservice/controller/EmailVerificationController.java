package com.learning.authservice.controller;

import com.learning.authservice.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for email verification operations.
 * Handles resending verification emails and confirming signup codes.
 * 
 * Security: Public endpoints (no authentication required)
 * Rate limiting should be applied at Gateway level
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * Resend verification email to user.
     * 
     * @param request Email address to resend verification to
     * @return Success message
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationEmail(@Valid @RequestBody ResendVerificationRequest request) {
        log.info("Resending verification email to: {}", request.getEmail());

        emailVerificationService.resendVerificationCode(request.getEmail());

        return ResponseEntity.ok()
                .body(new MessageResponse("Verification email sent. Please check your inbox."));
    }

    /**
     * Confirm signup with verification code (alternative to email link).
     * 
     * @param request Email and verification code
     * @return Success message
     */
    @PostMapping("/confirm-signup")
    public ResponseEntity<?> confirmSignup(@Valid @RequestBody ConfirmSignupRequest request) {
        log.info("Confirming signup for: {}", request.getEmail());

        emailVerificationService.confirmSignup(request.getEmail(), request.getCode());

        return ResponseEntity.ok()
                .body(new MessageResponse("Email verified successfully. You can now login."));
    }

    /**
     * Request DTO for resending verification email.
     */
    @Data
    public static class ResendVerificationRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    /**
     * Request DTO for confirming signup.
     */
    @Data
    public static class ConfirmSignupRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Code is required")
        private String code;
    }

    /**
     * Simple message response DTO
     */
    private record MessageResponse(String message) {
    }
}
