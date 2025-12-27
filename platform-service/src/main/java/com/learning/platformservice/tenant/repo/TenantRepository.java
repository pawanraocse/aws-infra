package com.learning.platformservice.tenant.repo;

import com.learning.platformservice.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByStripeSubscriptionId(String stripeSubscriptionId);
}
