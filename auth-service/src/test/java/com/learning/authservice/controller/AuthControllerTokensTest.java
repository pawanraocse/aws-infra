package com.learning.authservice.controller;

import com.learning.authservice.dto.AuthResponseDto;
import com.learning.authservice.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;


/**
 * Unit tests for /tokens endpoint logic (NT-19).
 */
class AuthControllerTokensTest {

    private final AuthService authService = mock(AuthService.class);
    private final OAuth2AuthorizedClientService clientService = mock(OAuth2AuthorizedClientService.class);
    private final AuthController controller = new AuthController(authService, clientService);

    private DefaultOidcUser oidcUser(String sub, String email) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token-" + sub, now, now.plusSeconds(3600), Map.of(
                "sub", sub,
                "email", email
        ));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("email", email));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo, "sub");
    }

    @Test
    @DisplayName("returns 401 JSON when OidcUser principal is null")
    void tokensUnauthorizedWhenPrincipalNull() {
        SecurityContextHolder.clearContext();
        ResponseEntity<?> response = controller.getTokens(null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isInstanceOf(String.class);
        String body = (String) response.getBody();
        assertThat(body).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(body).contains("No authenticated user");
    }

    @Test
    @DisplayName("returns 401 JSON with ACCESS_TOKEN_MISSING when authorized client absent")
    void tokensUnauthorizedWhenAccessTokenMissing() {
        var user = oidcUser("user1", "user1@example.com");
        SecurityContextHolder.setContext(new SecurityContextImpl(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                new org.springframework.security.oauth2.jwt.Jwt("dummy", Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg","none"), Map.of("sub","user1")), List.of(new SimpleGrantedAuthority("ROLE_USER"))
        )));
        Mockito.when(clientService.loadAuthorizedClient(anyString(), anyString())).thenReturn(null);

        ResponseEntity<?> response = controller.getTokens(user);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        String body = (String) response.getBody();
        assertThat(body).contains("ACCESS_TOKEN_MISSING");
    }

    @Test
    @DisplayName("returns distinct accessToken and idToken when authenticated")
    void tokensSuccessDistinctTokens() {
        var user = oidcUser("abc123", "test@example.com");

        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(
                        "abc123", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        ));

        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-xyz",
                now,
                now.plusSeconds(1800)
        );

        // ✅ Correct Mockito usage
        ClientRegistration registration = mock(ClientRegistration.class);
        when(registration.getRegistrationId()).thenReturn("cognito");

        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                registration,
                "abc123",
                accessToken,
                null
        );

        when(clientService.loadAuthorizedClient("cognito", "abc123"))
                .thenReturn(client);

        ResponseEntity<?> response = controller.getTokens(user);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(AuthResponseDto.class);

        AuthResponseDto dto = (AuthResponseDto) response.getBody();
        assertThat(dto.getAccessToken()).isEqualTo("access-token-xyz");
        assertThat(dto.getIdToken()).startsWith("id-token-");
        assertThat(dto.getIdToken()).isNotEqualTo(dto.getAccessToken());
        assertThat(dto.getExpiresIn()).isBetween(1700L, 1800L);
        assertThat(dto.getEmail()).isEqualTo("test@example.com");
        assertThat(dto.getUserId()).isEqualTo("abc123");
    }


}

