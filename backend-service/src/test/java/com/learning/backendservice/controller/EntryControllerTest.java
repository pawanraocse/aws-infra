package com.learning.backendservice.controller;

import com.learning.backendservice.BaseControllerTest;
import com.learning.backendservice.dto.EntryRequestDto;
import com.learning.backendservice.entity.Entry;
import com.learning.backendservice.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class EntryControllerTest extends BaseControllerTest {

    @Autowired
    private EntryRepository entryRepository;

    @BeforeEach
    void cleanup() {
        entryRepository.deleteAll();
    }

    @Test
    void shouldCreateEntry() throws Exception {
        // Given
        EntryRequestDto request = new EntryRequestDto("test-key", "test-value");

        // When/Then
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.key").value("test-key"))
                .andExpect(jsonPath("$.value").value("test-value"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.createdBy").value("test-user"));
    }

    @Test
    void shouldReturn400WhenKeyIsEmpty() throws Exception {
        // Given
        EntryRequestDto request = new EntryRequestDto("", "value");

        // When/Then
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetAllEntries() throws Exception {
        // Given
        entryRepository.save(Entry.builder().key("key1").value("value1").build());
        entryRepository.save(Entry.builder().key("key2").value("value2").build());

        // When/Then
        mockMvc.perform(get("/api/entries"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldGetEntryById() throws Exception {
        // Given
        Entry saved = entryRepository.save(Entry.builder().key("find-me").value("found").build());

        // When/Then
        mockMvc.perform(get("/api/entries/{id}", saved.getId()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.key").value("find-me"))
                .andExpect(jsonPath("$.value").value("found"));
    }

    @Test
    void shouldReturn404WhenEntryNotFound() throws Exception {
        mockMvc.perform(get("/api/entries/{id}", 99999L))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateEntry() throws Exception {
        // Given
        Entry existing = entryRepository.save(Entry.builder().key("old-key").value("old-value").build());
        EntryRequestDto request = new EntryRequestDto("updated-key", "updated-value");

        // When/Then
        mockMvc.perform(put("/api/entries/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.key").value("updated-key"))
                .andExpect(jsonPath("$.value").value("updated-value"))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.updatedBy").value("test-user"));
    }

    @Test
    void shouldDeleteEntry() throws Exception {
        // Given
        Entry saved = entryRepository.save(Entry.builder().key("delete-me").value("value").build());

        // When/Then
        mockMvc.perform(delete("/api/entries/{id}", saved.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/entries/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentEntry() throws Exception {
        mockMvc.perform(delete("/api/entries/{id}", 99999L))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPreventDuplicateKeys() throws Exception {
        // Given - Create first entry
        EntryRequestDto request1 = new EntryRequestDto("duplicate-key", "value1");
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // When/Then - Try to create with same key
        EntryRequestDto request2 = new EntryRequestDto("duplicate-key", "value2");
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    void shouldSupportPagination() throws Exception {
        // Given - Create multiple entries
        for (int i = 1; i <= 25; i++) {
            entryRepository.save(Entry.builder()
                    .key("key-" + i)
                    .value("value-" + i)
                    .build());
        }

        // When/Then - Request page 0, size 10
        mockMvc.perform(get("/api/entries")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }
}
