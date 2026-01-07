package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Checker configuration")
public record CheckerConfigDto(
    @Schema(description = "Checker identifier")
    String checkerId,
    
    @Schema(description = "Whether the checker is enabled")
    boolean enabled,
    
    @Schema(description = "Threshold configuration")
    Map<String, Object> thresholds,
    
    @Schema(description = "Whitelist rules")
    List<WhitelistRuleDto> whitelistRules
) {}










