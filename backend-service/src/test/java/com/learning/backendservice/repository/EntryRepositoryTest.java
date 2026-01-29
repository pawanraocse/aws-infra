package com.learning.backendservice.repository;

import com.learning.backendservice.BaseIntegrationTest;
import com.learning.backendservice.entity.Entry;
import com.learning.common.infra.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EntryRepositoryTest extends BaseIntegrationTest {

    private static final String TEST_TENANT = "test-tenant-123";

    @Autowired
    private EntryRepository entryRepository;

    @BeforeEach
    void setUpTenant() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void shouldSaveAndFindEntry() {
        // Given
        Entry entry = Entry.builder()
                .tenantId(TEST_TENANT)
                .key("test-key")
                .value("test-value")
                .build();

        // When
        Entry saved = entryRepository.saveAndFlush(entry);
        Entry reloaded = entryRepository.findByTenantIdAndId(TEST_TENANT, saved.getId()).orElseThrow();

        // Then
        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getTenantId()).isEqualTo(TEST_TENANT);
        assertThat(reloaded.getKey()).isEqualTo("test-key");
        assertThat(reloaded.getValue()).isEqualTo("test-value");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getCreatedBy()).isEqualTo("test-user");
    }

    @Test
    void shouldFindByTenantIdAndKey() {
        // Given
        Entry entry = Entry.builder()
                .tenantId(TEST_TENANT)
                .key("find-me")
                .value("found")
                .build();
        entryRepository.save(entry);

        // When
        var result = entryRepository.findByTenantIdAndKey(TEST_TENANT, "find-me");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getValue()).isEqualTo("found");
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFoundForTenant() {
        // When
        var result = entryRepository.findByTenantIdAndKey(TEST_TENANT, "non-existent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotFindEntryFromDifferentTenant() {
        // Given - entry in different tenant
        Entry entry = Entry.builder()
                .tenantId("other-tenant")
                .key("isolated-key")
                .value("isolated")
                .build();
        entryRepository.save(entry);

        // When - searching in TEST_TENANT
        var result = entryRepository.findByTenantIdAndKey(TEST_TENANT, "isolated-key");

        // Then - should not find it
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCheckExistsByTenantIdAndKey() {
        // Given
        Entry entry = Entry.builder()
                .tenantId(TEST_TENANT)
                .key("exists-key")
                .value("value")
                .build();
        entryRepository.save(entry);

        // When/Then
        assertThat(entryRepository.existsByTenantIdAndKey(TEST_TENANT, "exists-key")).isTrue();
        assertThat(entryRepository.existsByTenantIdAndKey(TEST_TENANT, "not-exists")).isFalse();
        assertThat(entryRepository.existsByTenantIdAndKey("other-tenant", "exists-key")).isFalse();
    }

    @Test
    void shouldUpdateAuditFields() {
        // Given
        Entry entry = Entry.builder()
                .tenantId(TEST_TENANT)
                .key("update-test")
                .value("original")
                .build();
        Entry saved = entryRepository.saveAndFlush(entry);

        // When
        saved.setValue("updated");
        entryRepository.saveAndFlush(saved);
        Entry reloaded = entryRepository.findByTenantIdAndId(TEST_TENANT, saved.getId()).orElseThrow();

        // Then
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedBy()).isEqualTo("test-user");
    }
}
