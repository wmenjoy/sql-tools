package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for Whitelist Fields validation rule.
 * Used for YAML configuration loading.
 */
public class WhitelistFieldsConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.MEDIUM;
    private List<String> fields = new ArrayList<>();
    private Map<String, List<String>> byTable = new HashMap<>();
    private boolean enforceForUnknownTables = false;

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

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public Map<String, List<String>> getByTable() {
        return byTable;
    }

    public void setByTable(Map<String, List<String>> byTable) {
        this.byTable = byTable;
    }

    public boolean isEnforceForUnknownTables() {
        return enforceForUnknownTables;
    }

    public void setEnforceForUnknownTables(boolean enforceForUnknownTables) {
        this.enforceForUnknownTables = enforceForUnknownTables;
    }
}
