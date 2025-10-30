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
