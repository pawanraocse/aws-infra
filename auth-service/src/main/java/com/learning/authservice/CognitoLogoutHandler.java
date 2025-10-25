package com.learning.authservice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class CognitoLogoutHandler extends SimpleUrlLogoutSuccessHandler {

    private static final String DOMAIN = "https://my-multi-tenant-app-dev-us-east-1.auth.us-east-1.amazoncognito.com";
    private static final String LOGOUT_REDIRECT_URL = "http://localhost:8080/logout-out";
    private static final String USER_POOL_CLIENT_ID = "6l9k3ckq5epd75lf4mt509ckrk";

    /**
     * Here, we must implement the new logout URL request. We define what URL to send our request to, and set out client_id and logout_uri parameters.
     */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String targetUrl = UriComponentsBuilder
                .fromUri(URI.create(DOMAIN + "/logout"))
                .queryParam("client_id", USER_POOL_CLIENT_ID)
                .queryParam("logout_uri", LOGOUT_REDIRECT_URL)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
        log.info("operation=logout, userId={}, redirecting to Cognito logout URL: {}", authentication != null ? authentication.getName() : "anonymous", targetUrl);
        return targetUrl;
    }
}
