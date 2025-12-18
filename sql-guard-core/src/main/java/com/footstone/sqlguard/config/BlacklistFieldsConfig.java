package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for Blacklist Fields validation rule.
 * Used for YAML configuration loading.
 * Prevents direct WHERE conditions on sensitive fields like "deleted" or "status".
 */
public class BlacklistFieldsConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private Set<String> fields = new HashSet<>(Arrays.asList(
        "deleted", "del_flag", "status"
    ));
    public BlacklistFieldsConfig(boolean enabled, Set<String> fields) {
        this.enabled = enabled;
        this.fields = fields;
    }

    public BlacklistFieldsConfig(boolean enabled) {
        this.enabled = enabled;
        this.fields = new HashSet<>(Arrays.asList(
            "deleted", "del_flag", "status"
        ));
    }

    public BlacklistFieldsConfig() {
        this.enabled = true;
        this.fields = new HashSet<>(Arrays.asList(
            "deleted", "del_flag", "status"
        ));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Set<String> getFields() {
        return fields;
    }

    public void setFields(Set<String> fields) {
        this.fields = fields;
    }
}








