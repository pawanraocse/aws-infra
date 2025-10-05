package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class EntryServiceImplTest {
    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryServiceImpl entryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createEntry_shouldReturnResponseDto() {
        EntryRequestDto request = new EntryRequestDto("type", "invoice");
        Entry entry = Entry.builder()
                .id(1L)
                .key("type")
                .value("invoice")
                .build();
        when(entryRepository.save(any(Entry.class))).thenReturn(entry);
        EntryResponseDto response = entryService.createEntry(request);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.key()).isEqualTo("type");
        assertThat(response.value()).isEqualTo("invoice");
    }

    @Test
    void getEntries_shouldReturnPageOfResponseDto() {
        Entry entry = Entry.builder().id(1L).key("type").value("invoice").build();
        Page<Entry> page = new PageImpl<>(List.of(entry));
        when(entryRepository.findAll(any(PageRequest.class))).thenReturn(page);
        Page<EntryResponseDto> result = entryService.getEntries(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).key()).isEqualTo("type");
    }

    @Test
    void getEntryById_shouldReturnResponseDto() {
        Entry entry = Entry.builder().id(1L).key("type").value("invoice").build();
        when(entryRepository.findById(1L)).thenReturn(Optional.of(entry));
        Optional<EntryResponseDto> result = entryService.getEntryById(1L);
        assertThat(result).isPresent();
        assertThat(result.get().key()).isEqualTo("type");
    }

    @Test
    void updateEntry_shouldUpdateAndReturnResponseDto() {
        Entry entry = Entry.builder().id(1L).key("type").value("invoice").build();
        when(entryRepository.findById(1L)).thenReturn(Optional.of(entry));
        when(entryRepository.save(any(Entry.class))).thenReturn(entry);
        EntryRequestDto request = new EntryRequestDto("type", "payment");
        Optional<EntryResponseDto> result = entryService.updateEntry(1L, request);
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo("payment");
    }

    @Test
    void deleteEntry_shouldDeleteAndReturnTrue() {
        when(entryRepository.existsById(1L)).thenReturn(true);
        doNothing().when(entryRepository).deleteById(1L);
        boolean deleted = entryService.deleteEntry(1L);
        assertThat(deleted).isTrue();
    }

    @Test
    void deleteEntry_shouldReturnFalseIfNotFound() {
        when(entryRepository.existsById(2L)).thenReturn(false);
        boolean deleted = entryService.deleteEntry(2L);
        assertThat(deleted).isFalse();
    }
}
