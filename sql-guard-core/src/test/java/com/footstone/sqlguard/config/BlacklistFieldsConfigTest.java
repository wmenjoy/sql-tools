package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for BlacklistFieldsConfig.
 */
public class BlacklistFieldsConfigTest {

    @Test
    public void testDefaultConfiguration() {
        BlacklistFieldsConfig config = new BlacklistFieldsConfig();
        
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals(RiskLevel.HIGH, config.getRiskLevel(), "Default risk level should be HIGH");
        assertNotNull(config.getFields(), "Fields set should not be null");
        assertFalse(config.getFields().isEmpty(), "Default fields should not be empty");
        assertTrue(config.getFields().contains("deleted"), "Should contain 'deleted' field");
        assertTrue(config.getFields().contains("del_flag"), "Should contain 'del_flag' field");
        assertTrue(config.getFields().contains("status"), "Should contain 'status' field");
    }

    @Test
    public void testFieldsModification() {
        BlacklistFieldsConfig config = new BlacklistFieldsConfig();
        
        Set<String> newFields = new HashSet<>(Arrays.asList("password", "secret", "token"));
        config.setFields(newFields);
        assertEquals(3, config.getFields().size(), "Should have 3 fields");
        assertTrue(config.getFields().contains("password"), "Should contain 'password' field");
        assertFalse(config.getFields().contains("deleted"), "Should not contain old 'deleted' field");
    }

    @Test
    public void testEmptyFields() {
        BlacklistFieldsConfig config = new BlacklistFieldsConfig();
        
        config.setFields(new HashSet<>());
        assertTrue(config.getFields().isEmpty(), "Fields should be empty");
    }
}







