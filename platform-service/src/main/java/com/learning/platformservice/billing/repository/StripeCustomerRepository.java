package com.learning.platformservice.billing.repository;

import com.learning.platformservice.billing.entity.StripeCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, UUID> {

    Optional<StripeCustomer> findByTenantId(String tenantId);

    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);

    boolean existsByTenantId(String tenantId);
}
