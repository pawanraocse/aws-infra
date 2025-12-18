package com.learning.authservice.user.repository;

import com.learning.authservice.user.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TenantUser entity.
 */
@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, String> {

    Optional<TenantUser> findByEmail(String email);

    List<TenantUser> findByStatus(String status);

    @Query("SELECT u FROM TenantUser u WHERE u.status = 'ACTIVE' ORDER BY u.name")
    List<TenantUser> findAllActiveUsers();

    @Query("SELECT u FROM TenantUser u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<TenantUser> searchUsers(@Param("search") String search);

    long countByStatus(String status);
}
