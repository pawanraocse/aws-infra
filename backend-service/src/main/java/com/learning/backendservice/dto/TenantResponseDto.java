package com.learning.backendservice.dto;

import com.learning.backendservice.entity.Tenant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant response")
public class TenantResponseDto {

    @Schema(description = "Tenant UUID")
    private UUID id;

    @Schema(description = "Tenant identifier")
    private String tenantId;

    @Schema(description = "Tenant name")
    private String tenantName;

    @Schema(description = "Database schema name")
    private String schemaName;

    @Schema(description = "Tenant status")
    private Tenant.TenantStatus status;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Created by user ID")
    private String createdBy;
}
