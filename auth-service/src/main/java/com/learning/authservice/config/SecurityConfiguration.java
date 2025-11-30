package com.learning.authservice.config;

import com.learning.authservice.CognitoLogoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

        private final CognitoProperties cognitoProperties;

        @Value("${frontend.url:http://localhost:4200}")
        private String frontendUrl;

        private static final String REQUEST_ID_HEADER = "X-Request-Id";

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                CognitoLogoutHandler cognitoLogoutHandler = new CognitoLogoutHandler(cognitoProperties);

                SimpleUrlAuthenticationSuccessHandler successHandler = new SimpleUrlAuthenticationSuccessHandler();
                successHandler.setDefaultTargetUrl(frontendUrl + "/#/callback");
                successHandler.setAlwaysUseDefaultTargetUrl(true);

                http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authorizeHttpRequests(authz -> authz
                                                .requestMatchers("/", "/actuator/health", "/actuator/info",
                                                                "/logged-out", "/signup/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(successHandler)
                                                .failureHandler(authenticationFailureHandler()))
                                .logout(logout -> logout
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .logoutSuccessUrl("/logged-out")
                                                .deleteCookies("JSESSIONID")
                                                .logoutSuccessHandler(cognitoLogoutHandler))
                                .headers(h -> h
                                                .frameOptions(f -> f.deny())
                                                .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'")));

                return http.build();
        }

        private AuthenticationFailureHandler authenticationFailureHandler() {
                return (HttpServletRequest request,
                                HttpServletResponse response,
                                AuthenticationException exception) -> {

                        String requestId = getOrGenerateRequestId(request);
                        String message = escape(exception.getMessage());
                        if (message.length() > 512) {
                                message = message.substring(0, 512) + "...";
                        }

                        String body = String.format(
                                        "{\"timestamp\":\"%s\",\"status\":401,\"code\":\"OAUTH2_FAILURE\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                                        Instant.now(), message, requestId);

                        log.warn("OAUTH2_FAILURE requestId={} exception={} message={}",
                                        requestId, exception.getClass().getSimpleName(), message);

                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setHeader(REQUEST_ID_HEADER, requestId);
                        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                };
        }

        private String getOrGenerateRequestId(HttpServletRequest request) {
                String id = request.getHeader(REQUEST_ID_HEADER);
                return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        }

        private String escape(String v) {
                if (v == null)
                        return "";
                return v.replace("\"", " ").replace("\n", " ").replace("\r", " ");
        }
}
