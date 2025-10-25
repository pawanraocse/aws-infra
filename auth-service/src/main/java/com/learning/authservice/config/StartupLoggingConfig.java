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
        log.info("[Startup] server.port: {}", serverPort);
        log.info("[Startup] redirect-uri: {}", redirectUri);
        log.info("[Startup] Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        log.info("[Startup] All properties containing 'redirect':");
        for (var prop : env.getPropertySources()) {
            if (prop.getName().contains("applicationConfig")) {
                if (prop.containsProperty("spring.security.oauth2.client.registration.cognito.redirect-uri")) {
                    log.info("  {} = {}", "spring.security.oauth2.client.registration.cognito.redirect-uri", prop.getProperty("spring.security.oauth2.client.registration.cognito.redirect-uri"));
                }
            }
        }
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

