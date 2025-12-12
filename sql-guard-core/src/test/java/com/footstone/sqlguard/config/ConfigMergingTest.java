package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for configuration merging behavior.
 */
public class ConfigMergingTest {

    private final YamlConfigLoader loader = new YamlConfigLoader();

    @Test
    public void testPartialConfigMergesWithDefaults() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-partial.yml", true);
        
        // User overrides
        assertFalse(config.isEnabled(), "User override: enabled should be false");
        assertEquals("dev", config.getActiveStrategy(), "User override: strategy should be dev");
        assertFalse(config.getRules().getNoWhereClause().isEnabled(), 
                    "User override: noWhereClause should be disabled");
        assertEquals(RiskLevel.MEDIUM, config.getRules().getDummyCondition().getRiskLevel(), 
                     "User override: dummyCondition risk level should be MEDIUM");
        
        // Inherited defaults (not specified in valid-partial.yml)
        assertNotNull(config.getInterceptors(), "Should have default interceptors");
        assertNotNull(config.getDeduplication(), "Should have default deduplication");
        assertTrue(config.getDeduplication().isEnabled(), "Should inherit default deduplication enabled");
        assertEquals(1000, config.getDeduplication().getCacheSize(), 
                     "Should inherit default cache size");
    }

    @Test
    public void testEmptyConfigUsesDefaults() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("empty.yml", true);
        
        // Should have all defaults
        assertTrue(config.isEnabled(), "Should use default enabled=true");
        assertEquals("prod", config.getActiveStrategy(), "Should use default strategy=prod");
        assertNotNull(config.getInterceptors(), "Should have default interceptors");
        assertNotNull(config.getDeduplication(), "Should have default deduplication");
        assertNotNull(config.getRules(), "Should have default rules");
    }

    @Test
    public void testCompleteConfigOverridesDefaults() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-complete.yml", true);
        
        // All values from user config
        assertTrue(config.isEnabled());
        assertEquals("prod", config.getActiveStrategy());
        assertTrue(config.getInterceptors().getMybatis().isEnabled());
        assertEquals(1000, config.getDeduplication().getCacheSize());
        assertEquals(RiskLevel.CRITICAL, config.getRules().getNoWhereClause().getRiskLevel());
    }

    @Test
    public void testLoadWithoutMerging() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-partial.yml", false);
        
        // User values present
        assertFalse(config.isEnabled());
        assertEquals("dev", config.getActiveStrategy());
        
        // Default structure still created by YAML loader
        assertNotNull(config.getInterceptors());
        assertNotNull(config.getDeduplication());
    }
}
