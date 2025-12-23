package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Time-series data point for trends")
public record TrendDataPoint(
    @Schema(description = "Timestamp for this data point")
    Instant timestamp,
    
    @Schema(description = "Count value")
    long count,
    
    @Schema(description = "Average value (optional)")
    Double avgValue
) {}








