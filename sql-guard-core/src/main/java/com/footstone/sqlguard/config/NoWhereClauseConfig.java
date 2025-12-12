package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;

/**
 * Configuration for No WHERE Clause validation rule.
 * Used for YAML configuration loading.
 */
public class NoWhereClauseConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.CRITICAL;

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
}
