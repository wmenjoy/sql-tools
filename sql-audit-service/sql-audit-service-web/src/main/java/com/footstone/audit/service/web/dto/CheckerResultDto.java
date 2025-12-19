package com.footstone.audit.service.web.dto;

import com.footstone.sqlguard.audit.model.RiskScore;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Individual checker result")
public record CheckerResultDto(
    @Schema(description = "Checker identifier", example = "slow_query")
    String checkerId,
    
    @Schema(description = "Risk score from this checker")
    RiskScore riskScore,
    
    @Schema(description = "Error message if checker failed")
    String errorMessage,
    
    @Schema(description = "Whether the check was successful")
    boolean success
) {}


