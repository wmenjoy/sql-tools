package com.footstone.audit.service.web.dto;

import com.footstone.sqlguard.core.model.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Risky SQL statement information")
public record RiskySqlDto(
    @Schema(description = "SQL identifier (MD5 hash)")
    String sqlId,
    
    @Schema(description = "SQL statement")
    String sql,
    
    @Schema(description = "Number of times flagged")
    long flagCount,
    
    @Schema(description = "Highest risk level observed")
    RiskLevel maxRiskLevel,
    
    @Schema(description = "Associated mapper ID")
    String mapperId
) {}








