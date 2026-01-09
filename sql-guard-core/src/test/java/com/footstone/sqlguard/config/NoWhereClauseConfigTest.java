package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for NoWhereClauseConfig.
 */
public class NoWhereClauseConfigTest {

    @Test
    public void testDefaultConfiguration() {
        NoWhereClauseConfig config = new NoWhereClauseConfig();
        
        assertFalse(config.isEnabled(), "Should be disabled by default (opt-in design)");
        assertEquals(RiskLevel.CRITICAL, config.getRiskLevel(), "Default risk level should be CRITICAL");
    }

    @Test
    public void testConfigurationModification() {
        NoWhereClauseConfig config = new NoWhereClauseConfig();
        
        config.setEnabled(false);
        assertFalse(config.isEnabled(), "Should be disabled after setting");
        
        config.setRiskLevel(RiskLevel.HIGH);
        assertEquals(RiskLevel.HIGH, config.getRiskLevel(), "Risk level should be HIGH after setting");
    }

    @Test
    public void testAllRiskLevels() {
        NoWhereClauseConfig config = new NoWhereClauseConfig();
        
        for (RiskLevel level : RiskLevel.values()) {
            config.setRiskLevel(level);
            assertEquals(level, config.getRiskLevel(), "Risk level should match set value");
        }
    }
}




















