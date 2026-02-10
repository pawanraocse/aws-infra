package com.learning.common.infra.security;

import com.learning.common.grpc.auth.PermissionServiceGrpc;
import com.learning.common.grpc.auth.RoleLookupRequest;
import com.learning.common.grpc.auth.RoleLookupResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
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
import java.util.concurrent.TimeUnit;

/**
 * Implementation of RoleLookupService that calls auth-service.
 *
 * <p>Supports two transport modes:</p>
 * <ul>
 *   <li><strong>gRPC (default)</strong> — HTTP/2 + Protobuf, ~4x faster than REST</li>
 *   <li><strong>REST (fallback)</strong> — WebClient, used when gRPC is disabled or unavailable</li>
 * </ul>
 *
 * <p>Results are cached to avoid repeated calls for the same user.
 * Not used in test profile — see TestRoleLookupService.</p>
 */
@Service
@Profile("!test")
@Slf4j
public class RemoteRoleLookupService implements RoleLookupService {

    private static final Duration REST_LOOKUP_TIMEOUT = Duration.ofSeconds(2);
    private static final long GRPC_DEADLINE_SECONDS = 2;
    private static final String CACHE_NAME = "userRoles";

    private final WebClient.Builder webClientBuilder;

    @GrpcClient("auth-grpc")
    private PermissionServiceGrpc.PermissionServiceBlockingStub permissionStub;

    @Value("${app.grpc.enabled:true}")
    private boolean grpcEnabled;

    @Value("${auth.service.url:http://auth-service:8081}")
    private String authServiceUrl;

    public RemoteRoleLookupService(
            @Qualifier("internalWebClientBuilder") WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId + ':' + #tenantId", unless = "#result.isEmpty()")
    public Optional<String> getUserRole(String userId, String tenantId) {
        return getUserRole(userId, tenantId, null);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#userId + ':' + #tenantId + ':' + #groups", unless = "#result.isEmpty()")
    public Optional<String> getUserRole(String userId, String tenantId, String groups) {
        if (userId == null || userId.isBlank()) {
            log.debug("getUserRole called with empty userId");
            return Optional.empty();
        }

        if (grpcEnabled && permissionStub != null) {
            try {
                return lookupViaGrpc(userId, tenantId, groups);
            } catch (StatusRuntimeException e) {
                log.warn("gRPC role lookup failed (status={}), falling back to REST: {}",
                        e.getStatus().getCode(), e.getMessage());
                return lookupViaRest(userId, tenantId, groups);
            }
        }
        return lookupViaRest(userId, tenantId, groups);
    }

    /**
     * gRPC-based role lookup — ~4x faster than REST (Protobuf + HTTP/2).
     */
    private Optional<String> lookupViaGrpc(String userId, String tenantId, String groups) {
        log.debug("gRPC role lookup: userId={} tenantId={} groups={}", userId, tenantId, groups);

        RoleLookupRequest.Builder requestBuilder = RoleLookupRequest.newBuilder()
                .setUserId(userId)
                .setTenantId(tenantId != null ? tenantId : "");

        if (groups != null && !groups.isBlank()) {
            requestBuilder.setGroups(groups);
        }

        RoleLookupResponse response = permissionStub
                .withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .getUserRole(requestBuilder.build());

        if (response.getFound() && !response.getRoleId().isBlank()) {
            log.debug("gRPC role lookup success: userId={} roleId={}", userId, response.getRoleId());
            return Optional.of(response.getRoleId());
        }

        log.debug("gRPC: No role found for userId={}", userId);
        return Optional.empty();
    }

    /**
     * REST fallback for role lookup — used when gRPC is disabled or fails.
     */
    private Optional<String> lookupViaRest(String userId, String tenantId, String groups) {
        String url = authServiceUrl + "/auth/internal/users/" + userId + "/role";
        log.debug("REST role lookup: url={} tenantId={} groups={}", url, tenantId, groups);

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
                        log.warn("REST role lookup failed: status={}", clientResponse.statusCode());
                        return Mono.empty();
                    })
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, String>>() {
                    })
                    .timeout(REST_LOOKUP_TIMEOUT)
                    .block();

            if (response != null && response.containsKey("roleId")) {
                String roleId = response.get("roleId");
                if (roleId != null && !roleId.isBlank()) {
                    log.debug("REST role lookup success: userId={} roleId={}", userId, roleId);
                    return Optional.of(roleId);
                }
            }

            log.debug("REST: No role found for userId={}", userId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("REST role lookup failed for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
