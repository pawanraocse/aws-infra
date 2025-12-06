package com.learning.authservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.learning.authservice.service.EmailVerificationService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
     * @param email User's email address
     * @return Success message
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationEmail(
            @RequestParam @Email @NotBlank String email) {

        log.info("Resending verification email to: {}", email);

        emailVerificationService.resendVerificationCode(email);

        return ResponseEntity.ok()
                .body(new MessageResponse("Verification email sent. Please check your inbox."));
    }

    /**
     * Confirm signup with verification code (alternative to email link).
     * 
     * @param email User's email
     * @param code  Verification code from email
     * @return Success message
     */
    @PostMapping("/confirm-signup")
    public ResponseEntity<?> confirmSignup(
            @RequestParam @Email @NotBlank String email,
            @RequestParam @NotBlank String code) {

        log.info("Confirming signup for: {}", email);

        emailVerificationService.confirmSignup(email, code);

        return ResponseEntity.ok()
                .body(new MessageResponse("Email verified successfully. You can now login."));
    }

    /**
     * Simple message response DTO
     */
    private record MessageResponse(String message) {
    }
}
