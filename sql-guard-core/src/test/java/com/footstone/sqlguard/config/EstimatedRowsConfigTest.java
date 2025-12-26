package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for EstimatedRowsConfig.
 */
public class EstimatedRowsConfigTest {

    @Test
    public void testDefaultConfiguration() {
        EstimatedRowsConfig config = new EstimatedRowsConfig();
        
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals(RiskLevel.HIGH, config.getRiskLevel(), "Default risk level should be HIGH");
        assertNotNull(config.getThresholds(), "Thresholds map should not be null");
        assertFalse(config.getThresholds().isEmpty(), "Default thresholds should not be empty");
    }

    @Test
    public void testDefaultThresholds() {
        EstimatedRowsConfig config = new EstimatedRowsConfig();
        Map<SqlCommandType, Integer> thresholds = config.getThresholds();
        
        assertTrue(thresholds.containsKey(SqlCommandType.UPDATE), "Should have UPDATE threshold");
        assertTrue(thresholds.containsKey(SqlCommandType.DELETE), "Should have DELETE threshold");
        assertEquals(10000, thresholds.get(SqlCommandType.UPDATE), "UPDATE threshold should be 10000");
        assertEquals(10000, thresholds.get(SqlCommandType.DELETE), "DELETE threshold should be 10000");
    }

    @Test
    public void testThresholdsModification() {
        EstimatedRowsConfig config = new EstimatedRowsConfig();
        
        Map<SqlCommandType, Integer> newThresholds = new HashMap<>();
        newThresholds.put(SqlCommandType.UPDATE, 5000);
        newThresholds.put(SqlCommandType.DELETE, 3000);
        newThresholds.put(SqlCommandType.SELECT, 50000);
        
        config.setThresholds(newThresholds);
        assertEquals(3, config.getThresholds().size(), "Should have 3 thresholds");
        assertEquals(5000, config.getThresholds().get(SqlCommandType.UPDATE), 
                     "UPDATE threshold should be 5000");
        assertEquals(50000, config.getThresholds().get(SqlCommandType.SELECT), 
                     "SELECT threshold should be 50000");
    }

    @Test
    public void testEmptyThresholds() {
        EstimatedRowsConfig config = new EstimatedRowsConfig();
        
        config.setThresholds(new HashMap<>());
        assertTrue(config.getThresholds().isEmpty(), "Thresholds should be empty");
    }
}



















