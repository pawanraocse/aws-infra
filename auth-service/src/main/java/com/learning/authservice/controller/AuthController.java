package com.learning.authservice.controller;

import com.learning.authservice.dto.AuthRequestDto;
import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.dto.SignupRequestDto;
import com.learning.authservice.dto.UserInfoDto;
import com.learning.authservice.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final OAuth2AuthorizedClientService authorizedClientService; // NT-07 added

    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> getCurrentUser(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
        // Accept JWT in Authorization header for stateless auth
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    /**
     * Get JWT tokens from the current OAuth2 session.
     * This endpoint extracts the ID token and access token from the authenticated OIDC user.
     * Use this after successful OAuth2 login to get tokens for API calls.
     */
    @GetMapping("/tokens")
    public ResponseEntity<?> getTokens(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            log.warn("operation=getTokens status=unauthorized reason=no_principal");
            return unauthorizedError("UNAUTHORIZED", "No authenticated user");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.warn("operation=getTokens status=unauthorized reason=no_authentication");
            return unauthorizedError("UNAUTHORIZED", "No authentication context");
        }
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("cognito", auth.getName());
        if (client == null || client.getAccessToken() == null) {
            log.warn("operation=getTokens status=unauthorized reason=access_token_missing userId={}", oidcUser.getSubject());
            return unauthorizedError("ACCESS_TOKEN_MISSING", "Access token not available");
        }
        String accessToken = client.getAccessToken().getTokenValue();
        String idToken = oidcUser.getIdToken().getTokenValue();
        String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
        Instant expiresAt = client.getAccessToken().getExpiresAt();
        Long expiresIn = expiresAt != null ? Duration.between(Instant.now(), expiresAt).getSeconds() : null;

        AuthResponseDto response = new AuthResponseDto();
        response.setAccessToken(accessToken);
        response.setIdToken(idToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(expiresIn);
        response.setUserId(oidcUser.getSubject());
        response.setEmail(oidcUser.getEmail());
        log.info("operation=getTokens status=success userId={}", oidcUser.getSubject());
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<String> unauthorizedError(String code, String message) {
        String body = String.format("{\"timestamp\":\"%s\",\"status\":401,\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                Instant.now(), code, message.replace("\"","\\\""), java.util.UUID.randomUUID());
        return ResponseEntity.status(401).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto request, HttpServletResponse response) {
        log.info("operation=login, email={}", request.getEmail());
        AuthResponseDto authResponse = authService.login(request);
        // Set JWT as HttpOnly cookie for browser clients using Jakarta API
        if (authResponse.getAccessToken() != null) {
            Cookie jwtCookie = new Cookie("JWT", authResponse.getAccessToken());
            jwtCookie.setHttpOnly(true);
            jwtCookie.setSecure(true);
            jwtCookie.setPath("/");
            jwtCookie.setMaxAge(authResponse.getExpiresIn() != null ? authResponse.getExpiresIn().intValue() : 900);
            response.addCookie(jwtCookie);
        }
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody SignupRequestDto request, HttpServletResponse response) {
        log.info("operation=signup, email={}", request.getEmail());
        AuthResponseDto authResponse = authService.signup(request);
        // Set JWT as HttpOnly cookie for browser clients using Jakarta API
        if (authResponse.getAccessToken() != null) {
            Cookie jwtCookie = new Cookie("JWT", authResponse.getAccessToken());
            jwtCookie.setHttpOnly(true);
            jwtCookie.setSecure(true);
            jwtCookie.setPath("/");
            jwtCookie.setMaxAge(authResponse.getExpiresIn() != null ? authResponse.getExpiresIn().intValue() : 900);
            response.addCookie(jwtCookie);
        }
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout();
        // Remove JWT cookie using Jakarta API
        Cookie jwtCookie = new Cookie("JWT", null);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0);
        response.addCookie(jwtCookie);
        return ResponseEntity.noContent().build();
    }
}
