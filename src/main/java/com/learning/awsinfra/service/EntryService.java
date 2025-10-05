package com.learning.awsinfra.service;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface EntryService {
    EntryResponseDto createEntry(EntryRequestDto request, String userId, String requestId);

    Page<EntryResponseDto> getEntries(Pageable pageable, String filter, String userId, String requestId);

    Optional<EntryResponseDto> getEntryById(UUID id, String userId, String requestId);

    Optional<EntryResponseDto> updateEntry(UUID id, EntryRequestDto request, String userId, String requestId);

    boolean deleteEntry(UUID id, String userId, String requestId);
}

