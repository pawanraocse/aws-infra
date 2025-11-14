package com.learning.backendservice.repository;

import com.learning.backendservice.BaseIntegrationTest;
import com.learning.backendservice.entity.Entry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EntryRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private EntryRepository entryRepository;

    @Test
    void shouldSaveAndFindEntry() {
        // Given
        Entry entry = Entry.builder()
                .key("test-key")
                .value("test-value")
                .build();

        // When
        Entry saved = entryRepository.saveAndFlush(entry);
        Entry reloaded = entryRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getKey()).isEqualTo("test-key");
        assertThat(reloaded.getValue()).isEqualTo("test-value");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getCreatedBy()).isEqualTo("test-user");
    }

    @Test
    void shouldFindByKey() {
        // Given
        Entry entry = Entry.builder()
                .key("find-me")
                .value("found")
                .build();
        entryRepository.save(entry);

        // When
        var result = entryRepository.findByKey("find-me");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getValue()).isEqualTo("found");
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFound() {
        // When
        var result = entryRepository.findByKey("non-existent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldUpdateAuditFields() {
        // Given
        Entry entry = Entry.builder()
                .key("update-test")
                .value("original")
                .build();
        Entry saved = entryRepository.saveAndFlush(entry);

        // When
        saved.setValue("updated");
        entryRepository.saveAndFlush(saved);
        Entry reloaded = entryRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedBy()).isEqualTo("test-user");
    }
}
