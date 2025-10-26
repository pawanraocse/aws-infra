package com.learning.authservice;

import com.learning.authservice.config.CognitoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Cognito has a custom logout url.
 * See more information <a href="https://docs.aws.amazon.com/cognito/latest/developerguide/logout-endpoint.html">here</a>.
 */
@Slf4j
@RequiredArgsConstructor
public class CognitoLogoutHandler extends SimpleUrlLogoutSuccessHandler {
    private final CognitoProperties cognitoProperties;

    /**
     * Here, we must implement the new logout URL request. We define what URL to send our request to, and set out client_id and logout_uri parameters.
     */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String logoutUrl = cognitoProperties.getLogoutUrl();
        String logoutRedirectUrl = cognitoProperties.getLogoutRedirectUrl();
        String targetUrl = UriComponentsBuilder
                .fromUri(URI.create(logoutUrl))
                .queryParam("client_id", cognitoProperties.getClientId())
                .queryParam("logout_uri", logoutRedirectUrl)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        log.info("operation=logout, userId={}, domain={}, redirecting to Cognito logout URL: {}",
                authentication != null ? authentication.getName() : "anonymous",
                cognitoProperties.getDomain(),
                targetUrl);

        return targetUrl;
    }
}
