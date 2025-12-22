package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Whitelist rule configuration")
public record WhitelistRuleDto(
    @Schema(description = "Rule identifier")
    String ruleId,
    
    @Schema(description = "Rule type (SQL_PATTERN, MAPPER_ID, TABLE_NAME)")
    @NotBlank
    String ruleType,
    
    @Schema(description = "Pattern to match")
    @NotBlank
    String pattern,
    
    @Schema(description = "Rule description")
    String description,
    
    @Schema(description = "Whether the rule is active")
    boolean active
) {}



