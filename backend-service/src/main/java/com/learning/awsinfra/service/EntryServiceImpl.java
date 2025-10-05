package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import com.learning.awsinfra.entity.Entry;
import com.learning.awsinfra.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntryServiceImpl implements EntryService {
    private final EntryRepository entryRepository;

    @Override
    @Transactional
    public EntryResponseDto createEntry(EntryRequestDto request) {
        Entry entry = Entry.builder()
                .key(request.key())
                .value(request.value())
                .build();
        Entry savedEntry = entryRepository.save(entry);
        return toDto(savedEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EntryResponseDto> getEntries(Pageable pageable) {
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
        return entryRepository.findById(id).map(entry -> {
            entry.setKey(request.key());
            entry.setValue(request.value());
            entryRepository.save(entry);
            return toDto(entry);
        });
    }

    @Override
    @Transactional
    public boolean deleteEntry(Long id) {
        if (entryRepository.existsById(id)) {
            entryRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private EntryResponseDto toDto(Entry entry) {
        return new EntryResponseDto(entry.getId(), entry.getKey(), entry.getValue());
    }
}
