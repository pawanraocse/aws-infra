package com.learning.authservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of tenant lookup operation.
 * Contains the list of tenants and flow control information.
 */
@Data
@Builder
public class TenantLookupResult {

    /**
     * Email address that was looked up.
     */
    private String email;

    /**
     * List of tenants the user belongs to.
     */
    private List<TenantLookupResponse> tenants;

    /**
     * True if user has multiple tenants and must select one.
     * False if user has 0 or 1 tenant (auto-select or error).
     */
    private boolean requiresSelection;

    /**
     * The default tenant ID to auto-select if requiresSelection is false.
     */
    private String defaultTenantId;
}
