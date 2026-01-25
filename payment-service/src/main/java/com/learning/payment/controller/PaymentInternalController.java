package com.learning.payment.controller;

import com.learning.common.dto.MigrationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/internal")
@RequiredArgsConstructor
@Slf4j
public class PaymentInternalController {

    private final Flyway flyway;

    /**
     * Trigger migration for the shared payment database.
     * This is idempotent and safe to call multiple times.
     * It ensures the billing_account table exists.
     */
    @PostMapping("/migrate")
    public ResponseEntity<MigrationResult> migrate() {
        log.info("Received internal migration request");
        
        try {
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            String currentVersion = flyway.info().current() != null 
                    ? flyway.info().current().getVersion().toString() 
                    : "0";
            
            log.info("Migration completed. Applied: {}, Current Version: {}", migrationsApplied, currentVersion);
            
            return ResponseEntity.ok(new MigrationResult(
                    true,
                    migrationsApplied,
                    currentVersion,
                    null
            ));
        } catch (Exception e) {
            log.error("Migration failed", e);
            return ResponseEntity.internalServerError().body(new MigrationResult(
                    false,
                    0,
                    null,
                    null
            ));
        }
    }
}
