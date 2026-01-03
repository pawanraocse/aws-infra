package com.learning.common.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of RoleLookupService that calls auth-service.
 * Results are cached to avoid repeated calls for the same user.
 * 
 * <p>
 * Not used in test profile - see TestRoleLookupService.
 * </p>
 */
@Service
@Profile("!test")
@Slf4j
public class RemoteRoleLookupService implements RoleLookupService {

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);
    private static final String CACHE_NAME = "userRoles";

    private final WebClient.Builder webClientBuilder;

    @Value("${auth.service.url:http://auth-service:8081}")
    private String authServiceUrl;

    public RemoteRoleLookupService(
            @Qualifier("internalWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId + ':' + #tenantId", unless = "#result.isEmpty()")
    public Optional<String> getUserRole(String userId, String tenantId) {
        if (userId == null || userId.isBlank()) {
            log.debug("getUserRole called with empty userId");
            return Optional.empty();
        }

        String url = authServiceUrl + "/auth/internal/users/" + userId + "/role";
        log.debug("Looking up role: url={} tenantId={}", url, tenantId);

        try {
            WebClient webClient = webClientBuilder.build();
            Map<String, String> response = webClient.get()
                    .uri(url)
                    .header("X-Tenant-Id", tenantId != null ? tenantId : "system")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        log.warn("Role lookup failed: status={}", clientResponse.statusCode());
                        return Mono.empty();
                    })
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {
                    })
                    .timeout(LOOKUP_TIMEOUT)
                    .block();

            if (response != null && response.containsKey("roleId")) {
                String roleId = response.get("roleId");
                if (roleId != null && !roleId.isBlank()) {
                    log.debug("Role lookup success: userId={} roleId={}", userId, roleId);
                    return Optional.of(roleId);
                }
            }

            log.debug("No role found for userId={}", userId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Role lookup failed for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId + ':' + #tenantId + ':' + #groups", unless = "#result.isEmpty()")
    public Optional<String> getUserRole(String userId, String tenantId, String groups) {
        if (userId == null || userId.isBlank()) {
            log.debug("getUserRole called with empty userId");
            return Optional.empty();
        }

        String url = authServiceUrl + "/auth/internal/users/" + userId + "/role";
        log.debug("Looking up role: url={} tenantId={} groups={}", url, tenantId, groups);

        try {
            WebClient webClient = webClientBuilder.build();
            var requestSpec = webClient.get()
                    .uri(url)
                    .header("X-Tenant-Id", tenantId != null ? tenantId : "system");

            // Pass groups header for SSO group-to-role mapping
            if (groups != null && !groups.isBlank()) {
                requestSpec = requestSpec.header("X-Groups", groups);
            }

            Map<String, String> response = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        log.warn("Role lookup failed: status={}", clientResponse.statusCode());
                        return Mono.empty();
                    })
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {
                    })
                    .timeout(LOOKUP_TIMEOUT)
                    .block();

            if (response != null && response.containsKey("roleId")) {
                String roleId = response.get("roleId");
                if (roleId != null && !roleId.isBlank()) {
                    log.debug("Role lookup success: userId={} roleId={} (groups={})", userId, roleId, groups);
                    return Optional.of(roleId);
                }
            }

            log.debug("No role found for userId={}", userId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Role lookup failed for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
