package com.learning.backendservice.service;

import com.learning.backendservice.dto.EntryRequestDto;
import com.learning.backendservice.dto.EntryResponseDto;
import com.learning.backendservice.entity.Entry;
import com.learning.backendservice.repository.EntryRepository;
import com.learning.common.infra.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    private static final String TEST_TENANT = "test-tenant-123";

    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryServiceImpl entryService;

    @BeforeEach
    void setUpTenant() {
        TenantContext.setCurrentTenant(TEST_TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateEntry() {
        // Given
        EntryRequestDto request = new EntryRequestDto("test-key", "test-value");
        Entry savedEntry = Entry.builder()
                .id(1L)
                .tenantId(TEST_TENANT)
                .key("test-key")
                .value("test-value")
                .build();

        when(entryRepository.existsByTenantIdAndKey(TEST_TENANT, "test-key")).thenReturn(false);
        when(entryRepository.save(any(Entry.class))).thenReturn(savedEntry);

        // When
        EntryResponseDto result = entryService.createEntry(request);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getKey()).isEqualTo("test-key");
        verify(entryRepository).save(any(Entry.class));
    }

    @Test
    void shouldThrowExceptionWhenKeyExists() {
        // Given
        EntryRequestDto request = new EntryRequestDto("existing-key", "value");
        when(entryRepository.existsByTenantIdAndKey(TEST_TENANT, "existing-key")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> entryService.createEntry(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void shouldGetAllEntries() {
        // Given
        Entry entry = Entry.builder().id(1L).tenantId(TEST_TENANT).key("key").value("value").build();
        Page<Entry> page = new PageImpl<>(List.of(entry));
        when(entryRepository.findAll(any(PageRequest.class))).thenReturn(page);

        // When
        Page<EntryResponseDto> result = entryService.getEntries(PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getKey()).isEqualTo("key");
    }

    @Test
    void shouldGetEntryById() {
        // Given
        Entry entry = Entry.builder().id(1L).tenantId(TEST_TENANT).key("key").value("value").build();
        when(entryRepository.findByTenantIdAndId(TEST_TENANT, 1L)).thenReturn(Optional.of(entry));

        // When
        Optional<EntryResponseDto> result = entryService.getEntryById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void shouldUpdateEntry() {
        // Given
        Entry existing = Entry.builder().id(1L).tenantId(TEST_TENANT).key("old-key").value("old-value").build();
        EntryRequestDto request = new EntryRequestDto("new-key", "new-value");

        when(entryRepository.findByTenantIdAndId(TEST_TENANT, 1L)).thenReturn(Optional.of(existing));
        when(entryRepository.existsByTenantIdAndKey(TEST_TENANT, "new-key")).thenReturn(false);
        when(entryRepository.save(any(Entry.class))).thenReturn(existing);

        // When
        Optional<EntryResponseDto> result = entryService.updateEntry(1L, request);

        // Then
        assertThat(result).isPresent();
        verify(entryRepository).save(any(Entry.class));
    }

    @Test
    void shouldDeleteEntry() {
        // Given
        Entry entry = Entry.builder().id(1L).tenantId(TEST_TENANT).key("key").value("value").build();
        when(entryRepository.findByTenantIdAndId(TEST_TENANT, 1L)).thenReturn(Optional.of(entry));

        // When
        boolean result = entryService.deleteEntry(1L);

        // Then
        assertThat(result).isTrue();
        verify(entryRepository).deleteById(1L);
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentEntry() {
        // Given
        when(entryRepository.findByTenantIdAndId(TEST_TENANT, 999L)).thenReturn(Optional.empty());

        // When
        boolean result = entryService.deleteEntry(999L);

        // Then
        assertThat(result).isFalse();
        verify(entryRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowWhenNoTenantContext() {
        // Given - clear tenant context
        TenantContext.clear();
        EntryRequestDto request = new EntryRequestDto("key", "value");

        // When/Then
        assertThatThrownBy(() -> entryService.createEntry(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No tenant context");
    }
}
