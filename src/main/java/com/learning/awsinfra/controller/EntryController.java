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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/entries")
@Validated
public class EntryController {
    private final EntryService entryService;

    @Operation(summary = "Create Entry", description = "Creates a new Entry.")
    @ApiResponse(responseCode = "201", description = "Entry created successfully", content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<EntryResponseDto> createEntry(@Valid @RequestBody EntryRequestDto request,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            EntryResponseDto response = entryService.createEntry(request, userId, requestId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            log.error("[{}][{}][createEntry] Error: {}", userId, requestId, ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(null); // Error handled by global exception handler
        }
    }

    @Operation(summary = "Get Entries", description = "Retrieves paginated list of Entries with optional filters.")
    @ApiResponse(responseCode = "200", description = "Entries retrieved", content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @GetMapping
    public ResponseEntity<Page<EntryResponseDto>> getEntries(
            @Parameter(description = "Pagination and sorting") @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minAmount,
            @RequestParam(required = false) String createdAfter,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        StringBuilder filter = new StringBuilder();
        if (type != null) filter.append("type=").append(type).append(",");
        if (minAmount != null) filter.append("minAmount=").append(minAmount).append(",");
        if (createdAfter != null) filter.append("createdAfter=").append(createdAfter).append(",");
        if (filter.length() > 0) filter.setLength(filter.length() - 1); // Remove trailing comma
        try {
            Page<EntryResponseDto> page = entryService.getEntries(pageable, filter.toString(), userId, requestId);
            return ResponseEntity.ok(page);
        } catch (Exception ex) {
            log.error("[{}][{}][getEntries] Error: {}", userId, requestId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Page.empty());
        }
    }

    @Operation(summary = "Get Entry by ID", description = "Retrieves an Entry by its UUID.")
    @ApiResponse(responseCode = "200", description = "Entry found", content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Entry not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<EntryResponseDto> getEntryById(@Parameter(description = "Entry UUID") @PathVariable String id,
                                          @RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            return entryService.getEntryById(UUID.fromString(id), userId, requestId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
        } catch (Exception ex) {
            log.error("[{}][{}][getEntryById] Error: {}", userId, requestId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "Update Entry", description = "Updates an existing Entry.")
    @ApiResponse(responseCode = "200", description = "Entry updated", content = @Content(schema = @Schema(implementation = EntryResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Entry not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public ResponseEntity<EntryResponseDto> updateEntry(@Parameter(description = "Entry UUID") @PathVariable String id,
                                         @Valid @RequestBody EntryRequestDto request,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            return entryService.updateEntry(UUID.fromString(id), request, userId, requestId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
        } catch (Exception ex) {
            log.error("[{}][{}][updateEntry] Error: {}", userId, requestId, ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @Operation(summary = "Delete Entry", description = "Deletes an Entry by its UUID.")
    @ApiResponse(responseCode = "204", description = "Entry deleted")
    @ApiResponse(responseCode = "404", description = "Entry not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@Parameter(description = "Entry UUID") @PathVariable String id,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            boolean deleted = entryService.deleteEntry(UUID.fromString(id), userId, requestId);
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception ex) {
            log.error("[{}][{}][deleteEntry] Error: {}", userId, requestId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
