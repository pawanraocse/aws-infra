package com.learning.authservice.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class StartupLoggingConfig extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(StartupLoggingConfig.class);

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.security.oauth2.client.registration.cognito.redirect-uri}")
    private String redirectUri;

    private final ConfigurableEnvironment env;

    public StartupLoggingConfig(ConfigurableEnvironment env) {
        this.env = env;
    }

    @PostConstruct
    public void logStartupConfig() {
        log.info("=".repeat(80));
        log.info("[Startup] Application Configuration");
        log.info("=".repeat(80));
        log.info("[Startup] server.port: {}", serverPort);
        log.info("[Startup] redirect-uri: {}", redirectUri);
        log.info("[Startup] Active profiles: {}", Arrays.toString(env.getActiveProfiles()));

        log.info("-".repeat(80));
        log.info("[Startup] Cognito Configuration (from Environment)");
        log.info("-".repeat(80));
        log.info("[Startup] COGNITO_USER_POOL_ID: {}", maskValue(System.getenv("COGNITO_USER_POOL_ID")));
        log.info("[Startup] COGNITO_CLIENT_ID: {}", maskValue(System.getenv("COGNITO_CLIENT_ID")));
        log.info("[Startup] COGNITO_CLIENT_SECRET: {}", maskSecret(System.getenv("COGNITO_CLIENT_SECRET")));
        log.info("[Startup] COGNITO_ISSUER_URI: {}", System.getenv("COGNITO_ISSUER_URI"));
        log.info("[Startup] COGNITO_DOMAIN: {}", System.getenv("COGNITO_DOMAIN"));
        log.info("[Startup] COGNITO_REDIRECT_URI: {}", System.getenv("COGNITO_REDIRECT_URI"));
        log.info("[Startup] AWS_REGION: {}", System.getenv("AWS_REGION"));
        log.info("=".repeat(80));
    }

    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "NOT_SET";
        }
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "NOT_SET";
        }
        return "***" + secret.substring(Math.max(0, secret.length() - 4));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().contains("/oauth2/authorize") || request.getRequestURI().contains("/auth/cognito/callback")) {
            log.info("[Request] {} {} | redirect_uri param: {}", request.getMethod(), request.getRequestURI(), request.getParameter("redirect_uri"));
        }
        filterChain.doFilter(request, response);
    }
}

