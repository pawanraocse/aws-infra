package com.learning.payment.repository;

import com.learning.payment.entity.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingAccountRepository extends JpaRepository<BillingAccount, String> {
    Optional<BillingAccount> findByCustomerId(String customerId);
}
