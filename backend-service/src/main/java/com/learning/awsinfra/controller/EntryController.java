package com.learning.awsinfra.controller;

import com.learning.awsinfra.dto.EntryRequestDto;
import com.learning.awsinfra.dto.EntryResponseDto;
import com.learning.awsinfra.dto.ErrorResponse;
import com.learning.awsinfra.service.EntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/entries")
@Validated
public class EntryController {
    private final EntryService entryService;

    @Operation(summary = "Create Entry", description = "Creates a new key-value Entry.")
    @ApiResponse(responseCode = "201", description = "Entry created successfully",
            content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<EntryResponseDto> createEntry(@Valid @RequestBody EntryRequestDto request) {
        EntryResponseDto response = entryService.createEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get Entries", description = "Retrieves paginated list of key-value Entries.")
    @ApiResponse(responseCode = "200", description = "Entries retrieved",
            content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @GetMapping
    public ResponseEntity<Page<EntryResponseDto>> getEntries(@PageableDefault(size = 20) Pageable pageable) {
        Page<EntryResponseDto> page = entryService.getEntries(pageable);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Get Entry by ID", description = "Retrieves an Entry by its ID.")
    @ApiResponse(responseCode = "200", description = "Entry found",
            content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Entry not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<EntryResponseDto> getEntryById(@Parameter(description = "Entry ID") @PathVariable Long id) {
        return entryService.getEntryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @Operation(summary = "Update Entry", description = "Updates an existing key-value Entry.")
    @ApiResponse(responseCode = "200", description = "Entry updated",
            content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Entry not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public ResponseEntity<EntryResponseDto> updateEntry(@Parameter(description = "Entry ID") @PathVariable Long id,
                                                        @Valid @RequestBody EntryRequestDto request) {
        return entryService.updateEntry(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @Operation(summary = "Delete Entry", description = "Deletes an Entry by its ID.")
    @ApiResponse(responseCode = "204", description = "Entry deleted")
    @ApiResponse(responseCode = "404", description = "Entry not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@Parameter(description = "Entry ID") @PathVariable Long id) {
        boolean deleted = entryService.deleteEntry(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
