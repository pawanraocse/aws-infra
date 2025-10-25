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

    private static final String DOMAIN = "https://us-east-1unwednqxl.auth.us-east-1.amazoncognito.com";
    private static final String LOGOUT_REDIRECT_URL = "http://localhost:8080/logged-out";
    private static final String USER_POOL_CLIENT_ID = "3lrb1rfnp3hd2hl0hjistjd30i";

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
