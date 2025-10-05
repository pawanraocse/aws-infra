package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class EntryServiceImplTest {
    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryServiceImpl entryService;

    private final String userId = "test-user";
    private final String requestId = "req-123";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createEntry_shouldReturnResponseDto() {
        EntryRequestDto request = new EntryRequestDto(Map.of("type", "invoice", "amount", 100));
        Entry entry = Entry.builder()
                .id(UUID.randomUUID())
                .metadata(Map.of("type", "invoice", "amount", "100"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(entryRepository.save(any(Entry.class))).thenReturn(entry);
        EntryResponseDto response = entryService.createEntry(request, userId, requestId);
        assertThat(response.id()).isNotNull();
        assertThat(response.metadata()).containsEntry("type", "invoice");
    }

    @Test
    void getEntries_shouldReturnPagedResponseDtos() {
        Entry entry = Entry.builder()
                .id(UUID.randomUUID())
                .metadata(Map.of("type", "invoice"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Page<Entry> page = new PageImpl<>(List.of(entry));
        when(entryRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(entryRepository.findAll(
                ArgumentMatchers.<Specification<Entry>>any(),
                any(PageRequest.class)
        )).thenReturn(page);
        Page<EntryResponseDto> result = entryService.getEntries(PageRequest.of(0, 10), null, userId, requestId);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).metadata()).containsEntry("type", "invoice");
    }

    @Test
    void getEntryById_shouldReturnResponseDtoIfFound() {
        UUID id = UUID.randomUUID();
        Entry entry = Entry.builder()
                .id(id)
                .metadata(Map.of("type", "invoice"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(entryRepository.findById(id)).thenReturn(Optional.of(entry));
        Optional<EntryResponseDto> result = entryService.getEntryById(id, userId, requestId);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id.toString());
    }

    @Test
    void updateEntry_shouldReturnUpdatedResponseDtoIfFound() {
        UUID id = UUID.randomUUID();
        Entry entry = Entry.builder()
                .id(id)
                .metadata(new HashMap<>(Map.of("type", "invoice")))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(entryRepository.findById(id)).thenReturn(Optional.of(entry));
        when(entryRepository.save(any(Entry.class))).thenReturn(entry);
        EntryRequestDto request = new EntryRequestDto(Map.of("type", "receipt"));
        Optional<EntryResponseDto> result = entryService.updateEntry(id, request, userId, requestId);
        assertThat(result).isPresent();
        assertThat(result.get().metadata()).containsEntry("type", "receipt");
    }

    @Test
    void deleteEntry_shouldReturnTrueIfDeleted() {
        UUID id = UUID.randomUUID();
        when(entryRepository.existsById(id)).thenReturn(true);
        doNothing().when(entryRepository).deleteById(id);
        boolean result = entryService.deleteEntry(id, userId, requestId);
        assertThat(result).isTrue();
    }

    @Test
    void deleteEntry_shouldReturnFalseIfNotFound() {
        UUID id = UUID.randomUUID();
        when(entryRepository.existsById(id)).thenReturn(false);
        boolean result = entryService.deleteEntry(id, userId, requestId);
        assertThat(result).isFalse();
    }
}
