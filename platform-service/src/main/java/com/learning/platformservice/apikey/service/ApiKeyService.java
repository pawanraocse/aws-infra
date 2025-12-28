package com.learning.platformservice.apikey.service;

import com.learning.platformservice.apikey.dto.ApiKeyResponse;
import com.learning.platformservice.apikey.dto.CreateApiKeyRequest;
import com.learning.platformservice.apikey.dto.CreateApiKeyResponse;
import com.learning.platformservice.apikey.dto.ValidatedApiKey;
import com.learning.platformservice.apikey.entity.ApiKey;
import com.learning.platformservice.apikey.entity.ApiKeyStatus;
import com.learning.platformservice.apikey.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX = "sk_live_";
    private static final int KEY_LENGTH = 32; // 32 bytes = 256 bits of entropy
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Create a new API key for the given tenant and user.
     * The raw key is returned ONLY in this response - it cannot be retrieved later.
     */
    @Transactional
    public CreateApiKeyResponse createApiKey(String tenantId, String userId, String userEmail,
            CreateApiKeyRequest request) {
        log.info("Creating API key: tenant={}, user={}, name={}",
                tenantId, maskEmail(userEmail), request.name());

        // Generate secure random key
        String rawKey = generateKey();
        String keyHash = hashKey(rawKey);
        String keyPrefix = KEY_PREFIX + rawKey.substring(KEY_PREFIX.length(),
                KEY_PREFIX.length() + 8);

        // Calculate expiration
        Instant expiresAt = Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS);

        // Create entity
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(tenantId);
        apiKey.setName(request.name());
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setCreatedByUserId(userId);
        apiKey.setCreatedByEmail(userEmail);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setRateLimitPerMinute(60); // Default, can be enhanced with tier-based limits
        apiKey.setStatus(ApiKeyStatus.ACTIVE);

        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("API key created: id={}, prefix={}", saved.getId(), keyPrefix);

        return new CreateApiKeyResponse(
                saved.getId(),
                saved.getName(),
                rawKey, // ⚠️ Only time raw key is returned
                saved.getExpiresAt(),
                saved.getCreatedAt());
    }

    /**
     * List all API keys for a tenant (excludes hash).
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(String tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Revoke an API key.
     */
    @Transactional
    public boolean revokeApiKey(String tenantId, UUID keyId) {
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(keyId);
        if (keyOpt.isEmpty()) {
            return false;
        }

        ApiKey key = keyOpt.get();
        if (!key.getTenantId().equals(tenantId)) {
            log.warn("Attempt to revoke key from wrong tenant: keyId={}, requestedTenant={}",
                    keyId, tenantId);
            return false;
        }

        key.setStatus(ApiKeyStatus.REVOKED);
        key.setRevokedAt(Instant.now());
        apiKeyRepository.save(key);

        log.info("API key revoked: id={}, tenant={}", keyId, tenantId);
        return true;
    }

    /**
     * Validate an API key (called by gateway).
     * Updates usage tracking on successful validation.
     */
    @Transactional
    public ValidatedApiKey validateApiKey(String rawKey) {
        if (!isValidKeyFormat(rawKey)) {
            return ValidatedApiKey.invalid("API_KEY_INVALID");
        }

        String keyHash = hashKey(rawKey);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHashAndStatus(keyHash, ApiKeyStatus.ACTIVE);

        if (keyOpt.isEmpty()) {
            // Check if key exists but is revoked/expired
            log.debug("API key not found or not active: prefix={}",
                    rawKey.substring(0, Math.min(20, rawKey.length())));
            return ValidatedApiKey.invalid("API_KEY_INVALID");
        }

        ApiKey key = keyOpt.get();

        // Check expiration
        if (Instant.now().isAfter(key.getExpiresAt())) {
            key.setStatus(ApiKeyStatus.EXPIRED);
            apiKeyRepository.save(key);
            return ValidatedApiKey.invalid("API_KEY_EXPIRED");
        }

        // Update usage tracking
        apiKeyRepository.updateUsage(key.getId(), Instant.now());

        return ValidatedApiKey.valid(
                key.getId().toString(),
                key.getTenantId(),
                key.getCreatedByUserId(),
                key.getCreatedByEmail(),
                key.getRateLimitPerMinute());
    }

    /**
     * Count active API keys for a tenant (for limit enforcement).
     */
    @Transactional(readOnly = true)
    public long countActiveKeys(String tenantId) {
        return apiKeyRepository.countByTenantIdAndStatus(tenantId, ApiKeyStatus.ACTIVE);
    }

    // =====================================================
    // Private helpers
    // =====================================================

    private String generateKey() {
        byte[] randomBytes = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String base64Key = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return KEY_PREFIX + base64Key;
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private boolean isValidKeyFormat(String key) {
        return key != null &&
                key.startsWith(KEY_PREFIX) &&
                key.length() > KEY_PREFIX.length() + 10;
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.getCreatedByEmail(),
                key.getRateLimitPerMinute(),
                key.getExpiresAt(),
                key.getLastUsedAt(),
                key.getUsageCount(),
                key.getCreatedAt(),
                key.getStatus().name());
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 4)
            return "***";
        int atIdx = email.indexOf('@');
        if (atIdx < 0)
            return email.substring(0, 2) + "***";
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }
}
