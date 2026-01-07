package com.footstone.audit.service.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Checker information")
public record CheckerInfoDto(
    @Schema(description = "Checker identifier")
    String checkerId,
    
    @Schema(description = "Checker display name")
    String name,
    
    @Schema(description = "Checker description")
    String description,
    
    @Schema(description = "Whether the checker is enabled")
    boolean enabled,
    
    @Schema(description = "Checker category")
    String category
) {}










