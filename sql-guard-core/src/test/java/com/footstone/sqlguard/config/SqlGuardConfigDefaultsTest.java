package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SqlGuardConfigDefaults.
 */
public class SqlGuardConfigDefaultsTest {

    @Test
    public void testGetDefaultConfig() {
        SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
        
        assertNotNull(config, "Default config should not be null");
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals("prod", config.getActiveStrategy(), "Default strategy should be prod");
    }

    @Test
    public void testDefaultInterceptors() {
        SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
        
        assertTrue(config.getInterceptors().getMybatis().isEnabled(), 
                   "MyBatis should be enabled by default");
        assertFalse(config.getInterceptors().getMybatisPlus().isEnabled(), 
                    "MyBatis-Plus should be disabled by default");
        assertTrue(config.getInterceptors().getJdbc().isEnabled(), 
                   "JDBC should be enabled by default");
        assertEquals("auto", config.getInterceptors().getJdbc().getType(), 
                     "JDBC type should be auto by default");
    }

    @Test
    public void testDefaultDeduplication() {
        SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
        
        assertTrue(config.getDeduplication().isEnabled(), 
                   "Deduplication should be enabled by default");
        assertEquals(1000, config.getDeduplication().getCacheSize(), 
                     "Default cache size should be 1000");
        assertEquals(100L, config.getDeduplication().getTtlMs(), 
                     "Default TTL should be 100ms");
    }

    @Test
    public void testDefaultRules() {
        SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
        SqlGuardConfig.RulesConfig rules = config.getRules();
        
        // NoWhereClause
        assertTrue(rules.getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.CRITICAL, rules.getNoWhereClause().getRiskLevel());
        
        // DummyCondition
        assertTrue(rules.getDummyCondition().isEnabled());
        assertEquals(RiskLevel.HIGH, rules.getDummyCondition().getRiskLevel());
        
        // BlacklistFields
        assertTrue(rules.getBlacklistFields().isEnabled());
        assertEquals(RiskLevel.HIGH, rules.getBlacklistFields().getRiskLevel());
        assertEquals(3, rules.getBlacklistFields().getFields().size());
        assertTrue(rules.getBlacklistFields().getFields().contains("deleted"));
        
        // WhitelistFields
        assertTrue(rules.getWhitelistFields().isEnabled());
        assertEquals(RiskLevel.MEDIUM, rules.getWhitelistFields().getRiskLevel());
        
        // PaginationAbuse
        assertTrue(rules.getPaginationAbuse().isEnabled());
        assertEquals(RiskLevel.HIGH, rules.getPaginationAbuse().getRiskLevel());
        
        // NoPagination
        assertTrue(rules.getNoPagination().isEnabled());
        assertEquals(RiskLevel.MEDIUM, rules.getNoPagination().getRiskLevel());
        
        // EstimatedRows
        assertTrue(rules.getEstimatedRows().isEnabled());
        assertEquals(RiskLevel.HIGH, rules.getEstimatedRows().getRiskLevel());
    }

    @Test
    public void testDefaultConfigIsValid() {
        SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
        assertDoesNotThrow(() -> config.validate(), "Default config should pass validation");
    }
}
