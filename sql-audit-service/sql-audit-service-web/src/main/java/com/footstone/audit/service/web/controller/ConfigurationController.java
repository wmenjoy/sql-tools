package com.footstone.audit.service.web.controller;

import com.footstone.audit.service.web.dto.*;
import com.footstone.audit.service.web.service.CheckerConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkers")
@Tag(name = "Checker Configuration", description = "API for managing checker configurations")
public class ConfigurationController {
    
    private final CheckerConfigurationService configService;
    
    public ConfigurationController(CheckerConfigurationService configService) {
        this.configService = configService;
    }
    
    @GetMapping
    @Operation(summary = "Get all registered checkers with status")
    public List<CheckerInfoDto> getAllCheckers() {
        return configService.getAllCheckers();
    }
    
    @GetMapping("/{checkerId}/config")
    @Operation(summary = "Get checker configuration")
    public CheckerConfigDto getCheckerConfig(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId) {
        return configService.getCheckerConfig(checkerId);
    }
    
    @PutMapping("/{checkerId}/config")
    @Operation(summary = "Update checker configuration")
    public CheckerConfigDto updateCheckerConfig(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId,
            
            @Valid @RequestBody CheckerConfigUpdateDto update) {
        return configService.updateCheckerConfig(checkerId, update);
    }
    
    @PostMapping("/{checkerId}/enable")
    @Operation(summary = "Enable a checker")
    public void enableChecker(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId) {
        configService.enableChecker(checkerId);
    }
    
    @PostMapping("/{checkerId}/disable")
    @Operation(summary = "Disable a checker")
    public void disableChecker(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId) {
        configService.disableChecker(checkerId);
    }
    
    @PutMapping("/{checkerId}/threshold/{thresholdKey}")
    @Operation(summary = "Update a specific threshold value")
    public void updateThreshold(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId,
            
            @Parameter(description = "Threshold key to update")
            @PathVariable String thresholdKey,
            
            @Parameter(description = "New threshold value")
            @RequestParam String value) {
        configService.updateThreshold(checkerId, thresholdKey, value);
    }
    
    @PostMapping("/{checkerId}/whitelist")
    @Operation(summary = "Add a whitelist rule")
    public void addWhitelistRule(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId,
            
            @Valid @RequestBody WhitelistRuleDto rule) {
        configService.addWhitelistRule(checkerId, rule);
    }
    
    @DeleteMapping("/{checkerId}/whitelist/{ruleId}")
    @Operation(summary = "Remove a whitelist rule")
    public void removeWhitelistRule(
            @Parameter(description = "Checker identifier")
            @PathVariable String checkerId,
            
            @Parameter(description = "Rule identifier")
            @PathVariable String ruleId) {
        configService.removeWhitelistRule(checkerId, ruleId);
    }
    
    @GetMapping("/audit-log")
    @Operation(summary = "Get configuration audit log")
    public List<CheckerConfigurationService.ConfigAuditEntry> getConfigAuditLog() {
        return configService.getConfigAuditLog();
    }
}



