package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for DummyConditionConfig.
 */
public class DummyConditionConfigTest {

    @Test
    public void testDefaultConfiguration() {
        DummyConditionConfig config = new DummyConditionConfig();
        
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals(RiskLevel.HIGH, config.getRiskLevel(), "Default risk level should be HIGH");
        assertNotNull(config.getPatterns(), "Patterns list should not be null");
        assertNotNull(config.getCustomPatterns(), "Custom patterns list should not be null");
        assertFalse(config.getPatterns().isEmpty(), "Default patterns should not be empty");
    }

    @Test
    public void testPatternsModification() {
        DummyConditionConfig config = new DummyConditionConfig();
        
        List<String> newPatterns = Arrays.asList("1=1", "2=2", "'a'='a'");
        config.setPatterns(newPatterns);
        assertEquals(3, config.getPatterns().size(), "Should have 3 patterns");
        assertTrue(config.getPatterns().contains("1=1"), "Should contain '1=1' pattern");
    }

    @Test
    public void testCustomPatternsModification() {
        DummyConditionConfig config = new DummyConditionConfig();
        
        List<String> customPatterns = Arrays.asList("custom1", "custom2");
        config.setCustomPatterns(customPatterns);
        assertEquals(2, config.getCustomPatterns().size(), "Should have 2 custom patterns");
        assertTrue(config.getCustomPatterns().contains("custom1"), "Should contain 'custom1' pattern");
    }
}












