package com.footstone.sqlguard.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SqlGuardConfig root configuration.
 * Validates default config creation, enabled flag toggle, activeStrategy setting,
 * interceptors config structure, and deduplication config.
 */
public class SqlGuardConfigTest {

    @Test
    public void testDefaultConfigCreation() {
        SqlGuardConfig config = new SqlGuardConfig();
        
        // Verify default values
        assertTrue(config.isEnabled(), "Default enabled should be true");
        assertEquals("prod", config.getActiveStrategy(), "Default activeStrategy should be 'prod'");
        assertNotNull(config.getInterceptors(), "Interceptors config should not be null");
        assertNotNull(config.getDeduplication(), "Deduplication config should not be null");
        assertNotNull(config.getRules(), "Rules config should not be null");
    }

    @Test
    public void testEnabledFlagToggle() {
        SqlGuardConfig config = new SqlGuardConfig();
        
        // Test setting enabled to false
        config.setEnabled(false);
        assertFalse(config.isEnabled(), "Enabled should be false after setting");
        
        // Test setting enabled to true
        config.setEnabled(true);
        assertTrue(config.isEnabled(), "Enabled should be true after setting");
    }

    @Test
    public void testActiveStrategySettings() {
        SqlGuardConfig config = new SqlGuardConfig();
        
        // Test dev strategy
        config.setActiveStrategy("dev");
        assertEquals("dev", config.getActiveStrategy(), "ActiveStrategy should be 'dev'");
        
        // Test test strategy
        config.setActiveStrategy("test");
        assertEquals("test", config.getActiveStrategy(), "ActiveStrategy should be 'test'");
        
        // Test prod strategy
        config.setActiveStrategy("prod");
        assertEquals("prod", config.getActiveStrategy(), "ActiveStrategy should be 'prod'");
    }

    @Test
    public void testInterceptorsConfigStructure() {
        SqlGuardConfig config = new SqlGuardConfig();
        SqlGuardConfig.InterceptorsConfig interceptors = config.getInterceptors();
        
        assertNotNull(interceptors, "Interceptors config should not be null");
        assertNotNull(interceptors.getMybatis(), "MyBatis config should not be null");
        assertNotNull(interceptors.getMybatisPlus(), "MyBatis-Plus config should not be null");
        assertNotNull(interceptors.getJdbc(), "JDBC config should not be null");
        
        // Test MyBatis config
        SqlGuardConfig.MyBatisConfig mybatisConfig = interceptors.getMybatis();
        assertTrue(mybatisConfig.isEnabled(), "MyBatis should be enabled by default");
        
        // Test MyBatis-Plus config
        SqlGuardConfig.MyBatisPlusConfig mybatisPlusConfig = interceptors.getMybatisPlus();
        assertFalse(mybatisPlusConfig.isEnabled(), "MyBatis-Plus should be disabled by default");
        
        // Test JDBC config
        SqlGuardConfig.JdbcConfig jdbcConfig = interceptors.getJdbc();
        assertTrue(jdbcConfig.isEnabled(), "JDBC should be enabled by default");
        assertEquals("auto", jdbcConfig.getType(), "JDBC type should be 'auto' by default");
    }

    @Test
    public void testInterceptorsConfigModification() {
        SqlGuardConfig config = new SqlGuardConfig();
        SqlGuardConfig.InterceptorsConfig interceptors = config.getInterceptors();
        
        // Modify MyBatis config
        interceptors.getMybatis().setEnabled(false);
        assertFalse(interceptors.getMybatis().isEnabled(), "MyBatis should be disabled after setting");
        
        // Modify MyBatis-Plus config
        interceptors.getMybatisPlus().setEnabled(true);
        assertTrue(interceptors.getMybatisPlus().isEnabled(), "MyBatis-Plus should be enabled after setting");
        
        // Modify JDBC config
        interceptors.getJdbc().setEnabled(false);
        assertFalse(interceptors.getJdbc().isEnabled(), "JDBC should be disabled after setting");
        
        interceptors.getJdbc().setType("manual");
        assertEquals("manual", interceptors.getJdbc().getType(), "JDBC type should be 'manual' after setting");
    }

    @Test
    public void testDeduplicationConfigValues() {
        SqlGuardConfig config = new SqlGuardConfig();
        SqlGuardConfig.DeduplicationConfig dedup = config.getDeduplication();
        
        assertNotNull(dedup, "Deduplication config should not be null");
        assertTrue(dedup.isEnabled(), "Deduplication should be enabled by default");
        assertEquals(1000, dedup.getCacheSize(), "Default cache size should be 1000");
        assertEquals(100L, dedup.getTtlMs(), "Default TTL should be 100ms");
    }

    @Test
    public void testDeduplicationConfigModification() {
        SqlGuardConfig config = new SqlGuardConfig();
        SqlGuardConfig.DeduplicationConfig dedup = config.getDeduplication();
        
        // Modify enabled flag
        dedup.setEnabled(false);
        assertFalse(dedup.isEnabled(), "Deduplication should be disabled after setting");
        
        // Modify cache size
        dedup.setCacheSize(5000);
        assertEquals(5000, dedup.getCacheSize(), "Cache size should be 5000 after setting");
        
        // Modify TTL
        dedup.setTtlMs(500L);
        assertEquals(500L, dedup.getTtlMs(), "TTL should be 500ms after setting");
    }

    @Test
    public void testRulesConfigStructure() {
        SqlGuardConfig config = new SqlGuardConfig();
        SqlGuardConfig.RulesConfig rules = config.getRules();
        
        assertNotNull(rules, "Rules config should not be null");
        // Rules config structure will be tested in detail in Step 2
    }

    @Test
    public void testFullConfigurationChain() {
        SqlGuardConfig config = new SqlGuardConfig();
        
        // Set all top-level properties
        config.setEnabled(false);
        config.setActiveStrategy("dev");
        
        // Modify interceptors
        config.getInterceptors().getMybatis().setEnabled(false);
        config.getInterceptors().getMybatisPlus().setEnabled(true);
        config.getInterceptors().getJdbc().setType("manual");
        
        // Modify deduplication
        config.getDeduplication().setEnabled(false);
        config.getDeduplication().setCacheSize(2000);
        config.getDeduplication().setTtlMs(200L);
        
        // Verify all changes
        assertFalse(config.isEnabled());
        assertEquals("dev", config.getActiveStrategy());
        assertFalse(config.getInterceptors().getMybatis().isEnabled());
        assertTrue(config.getInterceptors().getMybatisPlus().isEnabled());
        assertEquals("manual", config.getInterceptors().getJdbc().getType());
        assertFalse(config.getDeduplication().isEnabled());
        assertEquals(2000, config.getDeduplication().getCacheSize());
        assertEquals(200L, config.getDeduplication().getTtlMs());
    }
}







