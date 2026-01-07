package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Schema(description = "Checker configuration update request")
public record CheckerConfigUpdateDto(
    @Schema(description = "Whether to enable or disable the checker")
    Boolean enabled,
    
    @Schema(description = "Updated threshold values")
    Map<String, Object> thresholds
) {}










