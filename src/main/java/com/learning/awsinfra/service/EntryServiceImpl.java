package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import com.learning.awsinfra.specification.EntrySpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntryServiceImpl implements EntryService {
    private final EntryRepository entryRepository;

    @Override
    @Transactional
    public EntryResponseDto createEntry(EntryRequestDto request, String userId, String requestId) {
        log.info("[{}][{}][createEntry] Creating entry", userId, requestId);
        Entry entry = Entry.builder()
                .id(UUID.randomUUID())
                .metadata(request.metadata().entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey(), e -> String.valueOf(e.getValue())
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        entryRepository.save(entry);
        return toDto(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EntryResponseDto> getEntries(Pageable pageable, String filter, String userId, String requestId) {
        log.info("[{}][{}][getEntries] Retrieving entries with filter: {}", userId, requestId, filter);
        // Parse filter string (e.g., type=invoice,minAmount=100,createdAfter=2025-01-01T00:00:00Z)
        String type = null;
        Integer minAmount = null;
        Instant createdAfter = null;
        if (filter != null && !filter.isBlank()) {
            for (String part : filter.split(",")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    switch (kv[0]) {
                        case "type" -> type = kv[1];
                        case "minAmount" -> minAmount = Integer.valueOf(kv[1]);
                        case "createdAfter" -> createdAfter = Instant.parse(kv[1]);
                    }
                }
            }
        }
        Specification<Entry> spec = EntrySpecification.filterByParams(type, minAmount, createdAfter);
        return entryRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntryResponseDto> getEntryById(UUID id, String userId, String requestId) {
        log.info("[{}][{}][getEntryById] Retrieving entry {}", userId, requestId, id);
        return entryRepository.findById(id).map(this::toDto);
    }

    @Override
    @Transactional
    public Optional<EntryResponseDto> updateEntry(UUID id, EntryRequestDto request, String userId, String requestId) {
        log.info("[{}][{}][updateEntry] Updating entry {}", userId, requestId, id);
        return entryRepository.findById(id).map(entry -> {
            entry.getMetadata().clear();
            entry.getMetadata().putAll(request.metadata().entrySet().stream().collect(Collectors.toMap(
                    e -> e.getKey(), e -> String.valueOf(e.getValue())
            )));
            entry.setUpdatedAt(Instant.now());
            entryRepository.save(entry);
            return toDto(entry);
        });
    }

    @Override
    @Transactional
    public boolean deleteEntry(UUID id, String userId, String requestId) {
        log.info("[{}][{}][deleteEntry] Deleting entry {}", userId, requestId, id);
        if (entryRepository.existsById(id)) {
            entryRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private EntryResponseDto toDto(Entry entry) {
        return new EntryResponseDto(
                entry.getId().toString(),
                entry.getMetadata().entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey(), e -> e.getValue()
                )),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
