package com.learning.awsinfra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EntryControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntryRepository entryRepository;

    @BeforeEach
    void setup() {
        entryRepository.deleteAll();
    }

    @Test
    void createEntry_shouldReturnCreatedEntry() throws Exception {
        EntryRequestDto request = new EntryRequestDto("type", "invoice");
        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("type"))
                .andExpect(jsonPath("$.value").value("invoice"));
    }

    @Test
    void getEntries_shouldReturnPaginatedEntries() throws Exception {
        entryRepository.save(Entry.builder().key("type").value("invoice").build());
        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].key").value("type"));
    }

    @Test
    void getEntryById_shouldReturnEntry() throws Exception {
        Entry entry = entryRepository.save(Entry.builder().key("type").value("invoice").build());
        mockMvc.perform(get("/api/entries/{id}", entry.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("type"));
    }

    @Test
    void updateEntry_shouldReturnUpdatedEntry() throws Exception {
        Entry entry = entryRepository.save(Entry.builder().key("type").value("invoice").build());
        EntryRequestDto update = new EntryRequestDto("type", "payment");
        mockMvc.perform(put("/api/entries/{id}", entry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("payment"));
    }

    @Test
    void deleteEntry_shouldReturnNoContent() throws Exception {
        Entry entry = entryRepository.save(Entry.builder().key("type").value("invoice").build());
        mockMvc.perform(delete("/api/entries/{id}", entry.getId()))
                .andExpect(status().isNoContent());
        assertThat(entryRepository.findById(entry.getId())).isNotPresent();
    }

    @Test
    void getEntryById_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/entries/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateEntry_shouldReturnNotFound() throws Exception {
        EntryRequestDto update = new EntryRequestDto("type", "payment");
        mockMvc.perform(put("/api/entries/{id}", 9999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEntry_shouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/entries/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEntry_withInvalidInput_shouldReturnBadRequest() throws Exception {
        EntryRequestDto invalid = new EntryRequestDto("", "");
        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}

