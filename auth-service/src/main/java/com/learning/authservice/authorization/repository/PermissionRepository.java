package com.learning.authservice.authorization.repository;

import com.learning.authservice.authorization.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Permission entity.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {

    /**
     * Find permission by resource and action
     */
    Optional<Permission> findByResourceAndAction(String resource, String action);

    /**
     * Find all permissions for a specific resource
     */
    List<Permission> findByResource(String resource);

    /**
     * Check if permission exists
     */
    boolean existsByResourceAndAction(String resource, String action);
}
