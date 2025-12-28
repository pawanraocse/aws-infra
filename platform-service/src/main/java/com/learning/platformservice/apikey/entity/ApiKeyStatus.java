package com.learning.platformservice.apikey.entity;

/**
 * Status of an API key.
 */
public enum ApiKeyStatus {
    /**
     * Key is active and usable.
     */
    ACTIVE,

    /**
     * Key was manually revoked by user.
     */
    REVOKED,

    /**
     * Key has expired (past expires_at timestamp).
     */
    EXPIRED
}
