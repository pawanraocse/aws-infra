package com.learning.awsinfra.controller;

import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import com.learning.awsinfra.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MinimalIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private EntryRepository entryRepository;

    @Test
    void testRepositorySaveAndFind() {
        Entry entry = Entry.builder()
                .key("type")
                .value("test")
                .build();
        entryRepository.save(entry);
        assertThat(entryRepository.findById(entry.getId())).isPresent();
    }
}
