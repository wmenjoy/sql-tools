package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Per-checker statistics")
public record CheckerStatsDto(
    @Schema(description = "Checker identifier")
    String checkerId,
    
    @Schema(description = "Number of times triggered")
    long triggerCount,
    
    @Schema(description = "Success rate percentage")
    double successRate,
    
    @Schema(description = "Average processing time in milliseconds")
    double avgProcessingTimeMs
) {}








