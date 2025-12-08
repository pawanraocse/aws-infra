package com.learning.common.infra.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @InjectMocks
    private TenantContextFilter tenantContextFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Environment environment;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldExtractTenantFromHeader() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Tenant-Id")).thenReturn("t_header");

        // When
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("t_header");
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowRequest_WhenHeaderMissingInDevProfile() throws ServletException, IOException {
        // Given - dev profile allows missing tenant header
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/entries");
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true); // dev profile

        // When
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentTenant()).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantContextFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }
}
