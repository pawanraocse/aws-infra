package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EntryService {
    EntryResponseDto createEntry(EntryRequestDto request);

    Page<EntryResponseDto> getEntries(Pageable pageable);

    Optional<EntryResponseDto> getEntryById(Long id);

    Optional<EntryResponseDto> updateEntry(Long id, EntryRequestDto request);

    boolean deleteEntry(Long id);
}
