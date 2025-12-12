package com.footstone.sqlguard.config;

import java.util.Map;

import java.util.HashMap;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.RiskLevel;

/**
 * Configuration for Estimated Rows validation rule.
 * Used for YAML configuration loading.
 */
public class EstimatedRowsConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private long maxEstimatedRows = 10000;
    private Map<SqlCommandType, Integer> thresholds = new HashMap<SqlCommandType, Integer>() {{
        put(SqlCommandType.UPDATE, 10000);
        put(SqlCommandType.DELETE, 10000);
    }};

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

    public long getMaxEstimatedRows() {
        return maxEstimatedRows;
    }

    public void setMaxEstimatedRows(long maxEstimatedRows) {
        this.maxEstimatedRows = maxEstimatedRows;
    }
    public Map<SqlCommandType, Integer> getThresholds() {
        return thresholds;
    }

    public void setThresholds(Map<SqlCommandType, Integer> thresholds) {
        this.thresholds = thresholds;
    }
}
