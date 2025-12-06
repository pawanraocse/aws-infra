package com.learning.authservice.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.UserInfoDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AuthServiceImpl.getCurrentUser (NT-19 coverage expansion).
 */
class AuthServiceImplGetCurrentUserTest {

    private AuthServiceImpl authService(HttpServletRequest req, HttpServletResponse resp) {
        CognitoProperties props = new CognitoProperties();
        // minimal required properties to satisfy validation if invoked elsewhere
        props.setUserPoolId("pool");
        props.setDomain("domain");
        props.setRegion("us-east-1");
        props.setClientId("client");
        CognitoIdentityProviderClient cognitoIdentityProviderClient = Mockito.mock(CognitoIdentityProviderClient.class);
        org.springframework.web.reactive.function.client.WebClient webClient = Mockito
                .mock(org.springframework.web.reactive.function.client.WebClient.class);
        // Note: Not calling props.validate() to avoid throwing during these tests; we
        // only test getCurrentUser.
        return new AuthServiceImpl(props, req, resp, cognitoIdentityProviderClient, webClient);
    }

    private DefaultOidcUser oidcUser(String sub, String email, String name) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token-" + sub, now, now.plusSeconds(3600), Map.of(
                "sub", sub,
                "email", email,
                "name", name));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("email", email, "name", name));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo, "sub");
    }

    @Test
    @DisplayName("throws RuntimeException when authentication missing")
    void throwsWhenAuthenticationMissing() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        SecurityContextHolder.clearContext();
        AuthServiceImpl svc = authService(req, resp);
        assertThatThrownBy(svc::getCurrentUser)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not authenticated");
    }

    @Test
    @DisplayName("throws RuntimeException when principal not OidcUser")
    void throwsWhenPrincipalInvalid() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        // Mock request without X-User-Id header
        Mockito.when(req.getHeader("X-User-Id")).thenReturn(null);
        SecurityContextHolder.setContext(new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                "plainUser", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        AuthServiceImpl svc = authService(req, resp);
        assertThatThrownBy(svc::getCurrentUser)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not authenticated");
    }

    @Test
    @DisplayName("returns UserInfoDto when OidcUser present and authenticated")
    void returnsUserInfoDtoWhenValid() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);

        // Mock headers that getCurrentUser expects
        Mockito.when(req.getHeader("X-User-Id")).thenReturn("sub123");
        Mockito.when(req.getHeader("X-Email")).thenReturn("user@example.com");
        Mockito.when(req.getHeader("X-Username")).thenReturn("Test User");

        var user = oidcUser("sub123", "user@example.com", "Test User");
        SecurityContextHolder.setContext(new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                user, "N/A", user.getAuthorities())));
        AuthServiceImpl svc = authService(req, resp);
        UserInfoDto info = svc.getCurrentUser();
        assertThat(info).isNotNull();
        assertThat(info.getUserId()).isEqualTo("sub123");
        assertThat(info.getEmail()).isEqualTo("user@example.com");
        assertThat(info.getName()).contains("Test User");
    }
}
