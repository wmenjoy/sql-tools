package com.footstone.audit.service.web.dto;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.audit.model.RiskScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Audit report data transfer object")
public record AuditReportDto(
    @Schema(description = "Unique report identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    String reportId,
    
    @Schema(description = "SQL identifier", example = "UserMapper.selectById")
    String sqlId,
    
    @Schema(description = "SQL statement that was audited")
    String sql,
    
    @Schema(description = "Aggregated risk score")
    RiskScore aggregatedRiskScore,
    
    @Schema(description = "Risk level classification")
    RiskLevel riskLevel,
    
    @Schema(description = "Individual checker results")
    List<CheckerResultDto> checkerResults,
    
    @Schema(description = "Report creation timestamp")
    Instant createdAt,
    
    @Schema(description = "Execution time in milliseconds")
    Long executionTimeMs,
    
    @Schema(description = "Number of rows affected")
    Integer rowsAffected
) {}








