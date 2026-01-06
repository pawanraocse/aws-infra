package com.learning.common.infra.openfga;

import com.learning.common.dto.TenantDbConfig;
import com.learning.common.infra.tenant.TenantContext;
import com.learning.common.infra.tenant.TenantRegistryService;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenFGA client wrapper implementing SOLID principles.
 * Only active when openfga.enabled=true.
 * 
 * Multi-Tenant Support:
 * - Each tenant has their own OpenFGA store (store-per-tenant strategy)
 * - Store ID is looked up from TenantDbConfig.fgaStoreId at runtime
 * - Uses the current tenant from TenantContext to determine which store to use
 * 
 * Performance Optimization:
 * - Clients are cached per store ID to avoid re-creation overhead
 * - Cache is thread-safe using ConcurrentHashMap
 * 
 * SOLID Compliance:
 * - SRP: Single class wrapping SDK operations
 * - OCP: Closed for modification, open for extension via interfaces
 * - LSP: Can be substituted anywhere OpenFgaReader/Writer is expected
 * - ISP: Implements separate Reader/Writer interfaces
 * - DIP: Services depend on abstractions (interfaces), not this class
 * 
 * Service Usage:
 * - Backend-service: Inject OpenFgaReader (check permissions only)
 * - Auth-service: Inject OpenFgaWriter (manage tuples)
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "true")
public class OpenFgaClientWrapper implements OpenFgaReader, OpenFgaWriter {

    private final OpenFgaProperties properties;
    private final TenantRegistryService tenantRegistry;
    private final OpenFgaResilienceConfig resilience;

    // Cache clients per store ID to avoid per-request creation overhead
    private final Map<String, OpenFgaClient> clientCache = new ConcurrentHashMap<>();

    public OpenFgaClientWrapper(OpenFgaProperties properties,
            TenantRegistryService tenantRegistry,
            OpenFgaResilienceConfig resilience) {
        this.properties = Objects.requireNonNull(properties, "OpenFgaProperties cannot be null");
        this.tenantRegistry = Objects.requireNonNull(tenantRegistry, "TenantRegistryService cannot be null");
        this.resilience = Objects.requireNonNull(resilience, "OpenFgaResilienceConfig cannot be null");
        log.info("OpenFGA client wrapper initialized: url={}, resilience enabled",
                properties.getApiUrl());
    }

    /**
     * Get the current tenant's OpenFGA store ID.
     * 
     * @return Store ID for the current tenant, or empty if not configured
     */
    private Optional<String> getCurrentStoreId() {
        String tenantId = TenantContext.getCurrentTenant();

        // Fallback to global store ID if no tenant context (e.g. system tasks)
        if (tenantId == null || tenantId.isBlank()) {
            if (properties.getStoreId() != null && !properties.getStoreId().isBlank()) {
                log.debug("No tenant context, using global FGA store ID");
                return Optional.of(properties.getStoreId());
            }
            log.debug("No tenant context and no global store ID, cannot determine FGA store");
            return Optional.empty();
        }

        try {
            TenantDbConfig config = tenantRegistry.load(tenantId);

            // If tenant config missing or has no store ID, fallback to global default
            if (config == null || config.fgaStoreId() == null || config.fgaStoreId().isBlank()) {
                if (properties.getStoreId() != null && !properties.getStoreId().isBlank()) {
                    log.debug("Tenant {} has no FGA store ID, using global default", tenantId);
                    return Optional.of(properties.getStoreId());
                }
                log.debug("Tenant {} has no FGA store ID and no global default configured", tenantId);
                return Optional.empty();
            }
            return Optional.of(config.fgaStoreId());
        } catch (Exception e) {
            log.warn("Failed to load tenant config: {}, falling back to global default", e.getMessage());
            if (properties.getStoreId() != null && !properties.getStoreId().isBlank()) {
                return Optional.of(properties.getStoreId());
            }
            return Optional.empty();
        }
    }

    /**
     * Get or create an OpenFGA client for the given store ID.
     * Clients are cached to avoid per-request creation overhead.
     */
    private OpenFgaClient getOrCreateClient(String storeId) {
        return clientCache.computeIfAbsent(storeId, id -> {
            try {
                var configuration = new ClientConfiguration()
                        .apiUrl(properties.getApiUrl())
                        .storeId(id);

                if (properties.getAuthorizationModelId() != null && !properties.getAuthorizationModelId().isBlank()) {
                    configuration.authorizationModelId(properties.getAuthorizationModelId());
                }

                log.debug("Created OpenFGA client for store: {}", id);
                return new OpenFgaClient(configuration);
            } catch (Exception e) {
                log.error("Failed to create OpenFGA client for store {}: {}", id, e.getMessage());
                throw new OpenFgaException("Failed to create OpenFGA client", e);
            }
        });
    }

    /**
     * Get client for the current tenant's store.
     */
    private Optional<OpenFgaClient> getClientForCurrentTenant() {
        return getCurrentStoreId().map(this::getOrCreateClient);
    }

    // ========================================================================
    // Input Validation
    // ========================================================================

    private void validateCheckParams(String userId, String relation, String objectType, String objectId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException("relation cannot be null or blank");
        }
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectType cannot be null or blank");
        }
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId cannot be null or blank");
        }
    }

    // ========================================================================
    // OpenFgaReader Implementation
    // ========================================================================

    /**
     * Check if a user has a specific relation to an object.
     * 
     * @param userId     User identifier (e.g., "user-123")
     * @param relation   Relation to check (e.g., "viewer", "editor", "owner")
     * @param objectType Type of object (e.g., "folder", "document")
     * @param objectId   Object identifier
     * @return true if user has the relation, false otherwise (default deny)
     */
    @Override
    public boolean check(String userId, String relation, String objectType, String objectId) {
        validateCheckParams(userId, relation, objectType, objectId);

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            log.debug("No OpenFGA client available for tenant, denying access by default");
            return false;
        }

        // Use resilience wrapper for retry and circuit breaker
        return resilience.executeWithResilience("check", () -> {
            try {
                var request = new ClientCheckRequest()
                        .user("user:" + userId)
                        .relation(relation)
                        ._object(objectType + ":" + objectId);

                var response = clientOpt.get().check(request).get();
                boolean allowed = Boolean.TRUE.equals(response.getAllowed());

                log.debug("OpenFGA check: user={}, relation={}, object={}:{} -> {}",
                        userId, relation, objectType, objectId, allowed);

                return allowed;
            } catch (Exception e) {
                throw new RuntimeException("OpenFGA check failed", e);
            }
        }, false); // Fail-safe: deny on error
    }

    /**
     * List all objects of a type that a user has a specific relation to.
     */
    @Override
    public List<String> listObjects(String userId, String relation, String objectType) {
        if (userId == null || userId.isBlank() || relation == null || relation.isBlank()
                || objectType == null || objectType.isBlank()) {
            log.warn("Invalid parameters for listObjects, returning empty list");
            return List.of();
        }

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            return List.of();
        }

        try {
            var request = new ClientListObjectsRequest()
                    .user("user:" + userId)
                    .relation(relation)
                    .type(objectType);

            var response = clientOpt.get().listObjects(request).get();
            List<String> objects = response.getObjects();

            log.debug("OpenFGA listObjects: user={}, relation={}, type={} -> {} objects",
                    userId, relation, objectType, objects != null ? objects.size() : 0);

            return objects != null ? objects : List.of();
        } catch (Exception e) {
            log.error("OpenFGA listObjects failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Read all tuples (access grants) for a specific object.
     * Used to list who has access to a resource.
     * 
     * @param objectType Type of object (folder, document)
     * @param objectId   Object identifier
     * @return List of TupleInfo with userId and relation
     */
    public List<OpenFgaTupleService.TupleInfo> readTuples(String objectType, String objectId) {
        if (objectType == null || objectType.isBlank() || objectId == null || objectId.isBlank()) {
            log.warn("Invalid parameters for readTuples, returning empty list");
            return List.of();
        }

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            return List.of();
        }

        try {
            var request = new dev.openfga.sdk.api.client.model.ClientReadRequest()
                    ._object(objectType + ":" + objectId);

            var response = clientOpt.get().read(request).get();
            var tuples = response.getTuples();

            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            return tuples.stream()
                    .filter(t -> t.getKey() != null)
                    .map(t -> {
                        String user = t.getKey().getUser();
                        String relation = t.getKey().getRelation();
                        // Extract userId from "user:xyz" format
                        String userId = user != null && user.startsWith("user:")
                                ? user.substring(5)
                                : user;
                        return new OpenFgaTupleService.TupleInfo(userId, relation);
                    })
                    .toList();
        } catch (Exception e) {
            log.error("OpenFGA readTuples failed for {}:{}: {}", objectType, objectId, e.getMessage());
            return List.of();
        }
    }

    // ========================================================================
    // OpenFgaWriter Implementation
    // ========================================================================

    /**
     * Write a relationship tuple (grant access).
     * This operation is non-throwing to avoid breaking caller flows.
     * Errors are logged and should be monitored via observability.
     */
    @Override
    public void writeTuple(String userId, String relation, String objectType, String objectId) {
        validateCheckParams(userId, relation, objectType, objectId);

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            log.warn("No OpenFGA client available, cannot write tuple: user={} -> {} -> {}:{}",
                    userId, relation, objectType, objectId);
            return;
        }

        // Use resilience wrapper for retry and circuit breaker
        resilience.executeWithResilience("writeTuple", () -> {
            try {
                var tuple = new ClientTupleKey()
                        .user("user:" + userId)
                        .relation(relation)
                        ._object(objectType + ":" + objectId);

                var request = new ClientWriteRequest()
                        .writes(List.of(tuple));

                clientOpt.get().write(request).get();

                log.info("OpenFGA tuple written: user={} -> {} -> {}:{}",
                        userId, relation, objectType, objectId);
            } catch (Exception e) {
                throw new RuntimeException("OpenFGA writeTuple failed", e);
            }
        });
    }

    /**
     * Delete a relationship tuple (revoke access).
     * This operation is non-throwing to avoid breaking caller flows.
     */
    @Override
    public void deleteTuple(String userId, String relation, String objectType, String objectId) {
        validateCheckParams(userId, relation, objectType, objectId);

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            log.warn("No OpenFGA client available, cannot delete tuple: user={} -/-> {} -> {}:{}",
                    userId, relation, objectType, objectId);
            return;
        }

        // Use resilience wrapper for retry and circuit breaker
        resilience.executeWithResilience("deleteTuple", () -> {
            try {
                var tuple = new ClientTupleKey()
                        .user("user:" + userId)
                        .relation(relation)
                        ._object(objectType + ":" + objectId);

                var request = new ClientWriteRequest()
                        .deletes(List.of(tuple));

                clientOpt.get().write(request).get();

                log.info("OpenFGA tuple deleted: user={} -/-> {} -> {}:{}",
                        userId, relation, objectType, objectId);
            } catch (Exception e) {
                throw new RuntimeException("OpenFGA deleteTuple failed", e);
            }
        });
    }

    /**
     * Write a parent-child relationship for hierarchy.
     */
    @Override
    public void writeParentRelation(String childType, String childId, String parentType, String parentId) {
        if (childType == null || childId == null || parentType == null || parentId == null) {
            log.warn("Invalid parameters for writeParentRelation");
            return;
        }

        var clientOpt = getClientForCurrentTenant();
        if (clientOpt.isEmpty()) {
            log.warn("No OpenFGA client available, cannot write parent relation");
            return;
        }

        try {
            var tuple = new ClientTupleKey()
                    .user(parentType + ":" + parentId)
                    .relation("parent")
                    ._object(childType + ":" + childId);

            var request = new ClientWriteRequest()
                    .writes(List.of(tuple));

            clientOpt.get().write(request).get();

            log.info("OpenFGA parent relation: {}:{} -> parent -> {}:{}",
                    childType, childId, parentType, parentId);
        } catch (Exception e) {
            log.error("OpenFGA writeParentRelation failed: {}:{} -> parent -> {}:{}: {}",
                    childType, childId, parentType, parentId, e.getMessage());
        }
    }

    /**
     * Check if OpenFGA is enabled and properly configured.
     */
    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    // ========================================================================
    // Cache Management (for testing)
    // ========================================================================

    /**
     * Clear client cache. Useful for testing or when store configurations change.
     */
    public void clearClientCache() {
        clientCache.clear();
        log.debug("OpenFGA client cache cleared");
    }

    /**
     * Custom exception for OpenFGA operations.
     */
    public static class OpenFgaException extends RuntimeException {
        public OpenFgaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
