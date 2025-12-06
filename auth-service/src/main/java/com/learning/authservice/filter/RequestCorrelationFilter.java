package com.learning.authservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * NT-13 RequestCorrelationFilter (refined)
 * Ensures every request has an X-Request-Id header; propagates to MDC for
 * structured JSON logging.
 * Logs duration and completion status with source tracking ("generated" |
 * "upstream").
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_PATH = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        String source = "upstream";
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            source = "generated";
        }

        // Always echo header and set as request attribute for consistency
        response.setHeader(REQUEST_ID_HEADER, requestId);
        request.setAttribute(REQUEST_ID_HEADER, requestId);

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_PATH, request.getRequestURI());

        String userId = resolveUserId(request);
        if (userId != null) {
            MDC.put(MDC_USER_ID, userId);
        }

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();

            log.info(
                    "auth_request_completed {{\"requestId\":\"{}\",\"userId\":\"{}\",\"method\":\"{}\",\"path\":\"{}\",\"status\":{},\"durationMs\":{},\"source\":\"{}\"}}",
                    escape(requestId),
                    escape(userId),
                    request.getMethod(),
                    escape(request.getRequestURI()),
                    status,
                    duration,
                    source);

            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_PATH);
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            return null;
        return auth.getName();
    }

    private String escape(String v) {
        if (v == null)
            return "";
        return v.replace("\"", " ").replace("\n", " ").replace("\r", " ");
    }
}
