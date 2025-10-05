package com.learning.awsinfra.controller;

import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import com.learning.awsinfra.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MinimalIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private EntryRepository entryRepository;

    @Test
    void testRepositorySaveAndFind() {
        Entry entry = Entry.builder()
                .id(UUID.randomUUID())
                .metadata(java.util.Map.of("type", "test"))
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        entryRepository.save(entry);
        assertThat(entryRepository.findById(entry.getId())).isPresent();
    }
}

