package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for Dummy Condition validation rule.
 * Used for YAML configuration loading.
 */
public class DummyConditionConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private List<String> patterns = new ArrayList<>(Arrays.asList(
        "1=1",
        "1 = 1",
        "'1'='1'",
        "true",
        "'a'='a'"
    ));
    private List<String> customPatterns = new ArrayList<>();

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

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public List<String> getCustomPatterns() {
        return customPatterns;
    }

    public void setCustomPatterns(List<String> customPatterns) {
        this.customPatterns = customPatterns;
    }
}





