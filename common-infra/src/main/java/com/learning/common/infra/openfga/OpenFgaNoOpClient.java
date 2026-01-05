package com.learning.common.infra.openfga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * No-op implementation of OpenFGA interfaces when disabled.
 * 
 * SOLID Principle: Liskov Substitution (LSP)
 * - This can substitute for the real implementation
 * - All methods return safe defaults (false, empty lists)
 * 
 * Usage:
 * - When openfga.enabled=false, this bean is active
 * - Services can safely inject OpenFgaReader/Writer without null checks
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "openfga.enabled", havingValue = "false", matchIfMissing = true)
public class OpenFgaNoOpClient implements OpenFgaReader, OpenFgaWriter {

    public OpenFgaNoOpClient() {
        log.info("OpenFGA disabled - using no-op implementation");
    }

    @Override
    public boolean check(String userId, String relation, String resourceType, String resourceId) {
        log.debug("OpenFGA (no-op): check called but disabled");
        return false; // Deny by default when disabled
    }

    @Override
    public List<String> listObjects(String userId, String relation, String resourceType) {
        log.debug("OpenFGA (no-op): listObjects called but disabled");
        return List.of(); // Return empty list when disabled
    }

    @Override
    public void writeTuple(String userId, String relation, String resourceType, String resourceId) {
        log.debug("OpenFGA (no-op): writeTuple called but disabled");
        // No-op when disabled
    }

    @Override
    public void deleteTuple(String userId, String relation, String resourceType, String resourceId) {
        log.debug("OpenFGA (no-op): deleteTuple called but disabled");
        // No-op when disabled
    }

    @Override
    public void writeParentRelation(String childType, String childId, String parentType, String parentId) {
        log.debug("OpenFGA (no-op): writeParentRelation called but disabled");
        // No-op when disabled
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
