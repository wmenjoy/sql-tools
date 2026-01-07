package com.footstone.audit.service.web.dto;

import com.footstone.sqlguard.core.model.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Statistics overview data")
public record StatisticsOverviewDto(
    @Schema(description = "Total number of audits in the period")
    long totalAudits,
    
    @Schema(description = "Count of audits by risk level")
    Map<RiskLevel, Long> auditsByRiskLevel,
    
    @Schema(description = "Top triggered checkers with counts")
    Map<String, Long> topTriggeredCheckers,
    
    @Schema(description = "Average execution time in milliseconds")
    double avgExecutionTimeMs,
    
    @Schema(description = "Total number of errors")
    long errorCount
) {}










