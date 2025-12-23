package com.footstone.audit.service.web.dto;

import com.footstone.sqlguard.core.model.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Map;

@Schema(description = "Daily statistics breakdown")
public record DailyStatsDto(
    @Schema(description = "Date for this statistics")
    LocalDate date,
    
    @Schema(description = "Total audits on this day")
    long totalAudits,
    
    @Schema(description = "Audits by risk level")
    Map<RiskLevel, Long> byRiskLevel,
    
    @Schema(description = "Average execution time")
    double avgExecutionTimeMs
) {}








