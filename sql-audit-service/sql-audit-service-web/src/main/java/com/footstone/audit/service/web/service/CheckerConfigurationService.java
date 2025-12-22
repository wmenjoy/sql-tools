package com.footstone.audit.service.web.service;

import com.footstone.audit.service.web.dto.*;
import com.footstone.audit.service.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CheckerConfigurationService {
    
    // In-memory storage for demonstration - would be replaced by database in production
    private final Map<String, CheckerConfig> checkerConfigs = new ConcurrentHashMap<>();
    private final List<ConfigAuditEntry> auditLog = Collections.synchronizedList(new ArrayList<>());
    
    public CheckerConfigurationService() {
        // Initialize with default checkers
        initializeDefaultCheckers();
    }
    
    private void initializeDefaultCheckers() {
        registerChecker("slow_query", "Slow Query Checker", 
            "Detects queries with execution time exceeding threshold", "Performance",
            Map.of("thresholdMs", 1000));
        
        registerChecker("full_table_scan", "Full Table Scan Checker",
            "Detects queries that perform full table scans", "Performance",
            Map.of("maxRowsThreshold", 10000));
        
        registerChecker("missing_index", "Missing Index Checker",
            "Detects queries that could benefit from indexes", "Optimization",
            Map.of());
        
        registerChecker("dangerous_delete", "Dangerous Delete Checker",
            "Detects DELETE statements without WHERE clause", "Security",
            Map.of());
        
        registerChecker("sql_injection", "SQL Injection Checker",
            "Detects potential SQL injection patterns", "Security",
            Map.of("sensitivityLevel", "HIGH"));
    }
    
    private void registerChecker(String checkerId, String name, String description, 
                                  String category, Map<String, Object> defaultThresholds) {
        CheckerConfig config = new CheckerConfig(
            checkerId, name, description, category, true,
            new HashMap<>(defaultThresholds), new ArrayList<>()
        );
        checkerConfigs.put(checkerId, config);
    }
    
    public List<CheckerInfoDto> getAllCheckers() {
        return checkerConfigs.values().stream()
            .map(this::toInfoDto)
            .toList();
    }
    
    public CheckerConfigDto getCheckerConfig(String checkerId) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        return toConfigDto(config);
    }
    
    public CheckerConfigDto updateCheckerConfig(String checkerId, CheckerConfigUpdateDto update) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        
        // Validate thresholds
        if (update.thresholds() != null) {
            validateThresholds(checkerId, update.thresholds());
        }
        
        // Update configuration
        if (update.enabled() != null) {
            config.enabled = update.enabled();
        }
        if (update.thresholds() != null) {
            config.thresholds.putAll(update.thresholds());
        }
        
        // Record audit log
        recordConfigChange(checkerId, "UPDATE_CONFIG", update.toString());
        
        return toConfigDto(config);
    }
    
    private void validateThresholds(String checkerId, Map<String, Object> thresholds) {
        // Validate threshold values
        for (Map.Entry<String, Object> entry : thresholds.entrySet()) {
            if (entry.getValue() instanceof Number num) {
                if (num.doubleValue() < 0) {
                    throw new IllegalArgumentException(
                        "Invalid threshold value for " + entry.getKey() + ": must be non-negative");
                }
            }
        }
    }
    
    public void enableChecker(String checkerId) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        config.enabled = true;
        recordConfigChange(checkerId, "ENABLE", null);
    }
    
    public void disableChecker(String checkerId) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        config.enabled = false;
        recordConfigChange(checkerId, "DISABLE", null);
    }
    
    public void updateThreshold(String checkerId, String thresholdKey, Object value) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        
        if (value instanceof Number num && num.doubleValue() < 0) {
            throw new IllegalArgumentException("Threshold value must be non-negative");
        }
        
        config.thresholds.put(thresholdKey, value);
        recordConfigChange(checkerId, "UPDATE_THRESHOLD", thresholdKey + "=" + value);
    }
    
    public void addWhitelistRule(String checkerId, WhitelistRuleDto rule) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        
        WhitelistRule newRule = new WhitelistRule(
            rule.ruleId() != null ? rule.ruleId() : UUID.randomUUID().toString(),
            rule.ruleType(),
            rule.pattern(),
            rule.description(),
            rule.active()
        );
        config.whitelistRules.add(newRule);
        recordConfigChange(checkerId, "ADD_WHITELIST", rule.pattern());
    }
    
    public void removeWhitelistRule(String checkerId, String ruleId) {
        CheckerConfig config = checkerConfigs.get(checkerId);
        if (config == null) {
            throw new ResourceNotFoundException("Checker not found: " + checkerId);
        }
        
        boolean removed = config.whitelistRules.removeIf(r -> r.ruleId.equals(ruleId));
        if (!removed) {
            throw new ResourceNotFoundException("Whitelist rule not found: " + ruleId);
        }
        recordConfigChange(checkerId, "REMOVE_WHITELIST", ruleId);
    }
    
    public List<ConfigAuditEntry> getConfigAuditLog() {
        return new ArrayList<>(auditLog);
    }
    
    private void recordConfigChange(String checkerId, String action, String details) {
        auditLog.add(new ConfigAuditEntry(
            System.currentTimeMillis(),
            checkerId,
            action,
            details
        ));
    }
    
    private CheckerInfoDto toInfoDto(CheckerConfig config) {
        return new CheckerInfoDto(
            config.checkerId,
            config.name,
            config.description,
            config.enabled,
            config.category
        );
    }
    
    private CheckerConfigDto toConfigDto(CheckerConfig config) {
        List<WhitelistRuleDto> ruleDtos = config.whitelistRules.stream()
            .map(r -> new WhitelistRuleDto(r.ruleId, r.ruleType, r.pattern, r.description, r.active))
            .toList();
        
        return new CheckerConfigDto(
            config.checkerId,
            config.enabled,
            new HashMap<>(config.thresholds),
            ruleDtos
        );
    }
    
    // Internal classes
    private static class CheckerConfig {
        String checkerId;
        String name;
        String description;
        String category;
        boolean enabled;
        Map<String, Object> thresholds;
        List<WhitelistRule> whitelistRules;
        
        CheckerConfig(String checkerId, String name, String description, String category,
                      boolean enabled, Map<String, Object> thresholds, List<WhitelistRule> whitelistRules) {
            this.checkerId = checkerId;
            this.name = name;
            this.description = description;
            this.category = category;
            this.enabled = enabled;
            this.thresholds = thresholds;
            this.whitelistRules = whitelistRules;
        }
    }
    
    private static class WhitelistRule {
        String ruleId;
        String ruleType;
        String pattern;
        String description;
        boolean active;
        
        WhitelistRule(String ruleId, String ruleType, String pattern, String description, boolean active) {
            this.ruleId = ruleId;
            this.ruleType = ruleType;
            this.pattern = pattern;
            this.description = description;
            this.active = active;
        }
    }
    
    public record ConfigAuditEntry(long timestamp, String checkerId, String action, String details) {}
}



