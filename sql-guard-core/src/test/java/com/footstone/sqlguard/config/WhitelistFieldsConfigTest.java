package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for WhitelistFieldsConfig.
 */
public class WhitelistFieldsConfigTest {

    @Test
    public void testDefaultConfiguration() {
        WhitelistFieldsConfig config = new WhitelistFieldsConfig();
        
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals(RiskLevel.MEDIUM, config.getRiskLevel(), "Default risk level should be MEDIUM");
        assertNotNull(config.getFields(), "Fields set should not be null");
        assertNotNull(config.getByTable(), "ByTable map should not be null");
        assertFalse(config.isEnforceForUnknownTables(), "EnforceForUnknownTables should be false by default");
    }

    @Test
    public void testFieldsModification() {
        WhitelistFieldsConfig config = new WhitelistFieldsConfig();
        
        List<String> fields = Arrays.asList("id", "name", "email");
        config.setFields(fields);
        assertEquals(3, config.getFields().size(), "Should have 3 fields");
        assertTrue(config.getFields().contains("id"), "Should contain 'id' field");
    }

    @Test
    public void testByTableConfiguration() {
        WhitelistFieldsConfig config = new WhitelistFieldsConfig();
        
        Map<String, List<String>> byTable = new HashMap<>();
        byTable.put("users", Arrays.asList("id", "username", "email"));
        byTable.put("orders", Arrays.asList("id", "order_no", "amount"));
        
        config.setByTable(byTable);
        assertEquals(2, config.getByTable().size(), "Should have 2 table entries");
        assertEquals(3, config.getByTable().get("users").size(), "Users table should have 3 fields");
        assertTrue(config.getByTable().get("orders").contains("order_no"), "Orders should contain 'order_no'");
    }

    @Test
    public void testEnforceForUnknownTables() {
        WhitelistFieldsConfig config = new WhitelistFieldsConfig();
        
        config.setEnforceForUnknownTables(true);
        assertTrue(config.isEnforceForUnknownTables(), "EnforceForUnknownTables should be true");
        
        config.setEnforceForUnknownTables(false);
        assertFalse(config.isEnforceForUnknownTables(), "EnforceForUnknownTables should be false");
    }
}
