package com.learning.backendservice.service;

import com.learning.backendservice.dto.EntryRequestDto;
import com.learning.backendservice.dto.EntryResponseDto;
import com.learning.backendservice.entity.Entry;
import com.learning.backendservice.repository.EntryRepository;
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

    @Override
    @Transactional
    public EntryResponseDto createEntry(EntryRequestDto request) {
        log.debug("Creating entry with key: {}", request.getKey());

        if (entryRepository.existsByKey(request.getKey())) {
            throw new IllegalArgumentException("Entry with key '" + request.getKey() + "' already exists");
        }

        Entry entry = Entry.builder()
                .key(request.getKey())
                .value(request.getValue())
                .build();

        Entry saved = entryRepository.save(entry);
        log.info("Created entry with id: {}", saved.getId());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EntryResponseDto> getEntries(Pageable pageable) {
        log.debug("Fetching entries, page: {}", pageable.getPageNumber());
        return entryRepository.findAll(pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntryResponseDto> getEntryById(Long id) {
        return entryRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public Optional<EntryResponseDto> updateEntry(Long id, EntryRequestDto request) {
        return entryRepository.findById(id)
                .map(entry -> {
                    if (!entry.getKey().equals(request.getKey()) &&
                        entryRepository.existsByKey(request.getKey())) {
                        throw new IllegalArgumentException("Entry with key '" + request.getKey() + "' already exists");
                    }

                    entry.setKey(request.getKey());
                    entry.setValue(request.getValue());

                    Entry updated = entryRepository.save(entry);
                    log.info("Updated entry id: {}", id);
                    return toDto(updated);
                });
    }

    @Override
    @Transactional
    public boolean deleteEntry(Long id) {
        if (entryRepository.existsById(id)) {
            entryRepository.deleteById(id);
            log.info("Deleted entry id: {}", id);
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
