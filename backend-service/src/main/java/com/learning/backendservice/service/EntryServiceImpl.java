package com.learning.backendservice.service;

import com.learning.backendservice.dto.EntryRequestDto;
import com.learning.backendservice.dto.EntryResponseDto;
import com.learning.backendservice.entity.Entry;
import com.learning.backendservice.repository.EntryRepository;
import com.learning.common.infra.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryServiceImpl implements EntryService {

    private final EntryRepository entryRepository;

    private String getCurrentTenantId() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available");
        }
        return tenantId;
    }

    @Override
    @Transactional
    public EntryResponseDto createEntry(EntryRequestDto request) {
        String tenantId = getCurrentTenantId();
        log.debug("Creating entry with key: {} for tenant: {}", request.getKey(), tenantId);

        if (entryRepository.existsByTenantIdAndKey(tenantId, request.getKey())) {
            throw new IllegalArgumentException("Entry with key '" + request.getKey() + "' already exists");
        }

        Entry entry = Entry.builder()
                .tenantId(tenantId)
                .key(request.getKey())
                .value(request.getValue())
                .build();

        Entry saved = entryRepository.save(entry);
        log.info("Created entry with id: {} for tenant: {}", saved.getId(), tenantId);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EntryResponseDto> getEntries(Pageable pageable) {
        String tenantId = getCurrentTenantId();
        log.debug("Fetching entries for tenant: {}, page: {}", tenantId, pageable.getPageNumber());
        return entryRepository.findAll(pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntryResponseDto> getEntryById(Long id) {
        String tenantId = getCurrentTenantId();
        return entryRepository.findByTenantIdAndId(tenantId, id).map(this::toDto);
    }

    @Override
    @Transactional
    public Optional<EntryResponseDto> updateEntry(Long id, EntryRequestDto request) {
        String tenantId = getCurrentTenantId();
        return entryRepository.findByTenantIdAndId(tenantId, id)
                .map(entry -> {
                    if (!entry.getKey().equals(request.getKey()) &&
                        entryRepository.existsByTenantIdAndKey(tenantId, request.getKey())) {
                        throw new IllegalArgumentException("Entry with key '" + request.getKey() + "' already exists");
                    }

                    entry.setKey(request.getKey());
                    entry.setValue(request.getValue());

                    Entry updated = entryRepository.save(entry);
                    log.info("Updated entry id: {} for tenant: {}", id, tenantId);
                    return toDto(updated);
                });
    }

    @Override
    @Transactional
    public boolean deleteEntry(Long id) {
        String tenantId = getCurrentTenantId();
        if (entryRepository.findByTenantIdAndId(tenantId, id).isPresent()) {
            entryRepository.deleteById(id);
            log.info("Deleted entry id: {} for tenant: {}", id, tenantId);
            return true;
        }
        return false;
    }

    private EntryResponseDto toDto(Entry entry) {
        return EntryResponseDto.builder()
                .id(entry.getId())
                .key(entry.getKey())
                .value(entry.getValue())
                .createdAt(entry.getCreatedAt())
                .createdBy(entry.getCreatedBy())
                .updatedAt(entry.getUpdatedAt())
                .updatedBy(entry.getUpdatedBy())
                .build();
    }
}
