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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

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
    public ResponseEntity<AuthResponseDto> getTokens(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            log.warn("operation=getTokens, status=unauthorized, message=No authenticated user");
            return ResponseEntity.status(401).build();
        }

        log.info("operation=getTokens, userId={}, status=success", oidcUser.getSubject());

        // Extract tokens from OIDC user
        String idToken = oidcUser.getIdToken().getTokenValue();
        String accessToken = oidcUser.getIdToken().getTokenValue(); // In OIDC, ID token is used for authentication

        AuthResponseDto response = new AuthResponseDto();
        response.setAccessToken(idToken); // Use ID token as access token for API calls
        response.setTokenType("Bearer");
        response.setExpiresIn(oidcUser.getIdToken().getExpiresAt() != null ?
            oidcUser.getIdToken().getExpiresAt().getEpochSecond() - System.currentTimeMillis() / 1000 : 3600L);
        response.setUserId(oidcUser.getSubject());
        response.setEmail(oidcUser.getEmail());

        return ResponseEntity.ok(response);
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
