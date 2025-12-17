package com.footstone.sqlguard.spring.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SqlGuardProperties configuration binding.
 */
@SpringBootTest(
    classes = SqlGuardPropertiesTest.TestConfig.class,
    properties = {
        "sql-guard.enabled=true",
        "sql-guard.active-strategy=BLOCK",
        "sql-guard.interceptors.mybatis.enabled=true",
        "sql-guard.interceptors.mybatis-plus.enabled=true",
        "sql-guard.interceptors.jdbc.enabled=true",
        "sql-guard.deduplication.enabled=true",
        "sql-guard.deduplication.cache-size=2000",
        "sql-guard.deduplication.ttl-ms=200",
        "sql-guard.rules.no-where-clause.enabled=true",
        "sql-guard.rules.no-where-clause.risk-level=CRITICAL"
    }
)
public class SqlGuardPropertiesTest {

    @Autowired
    private SqlGuardProperties properties;

    @Test
    public void testYamlBinding_shouldBindAllProperties() {
        assertNotNull(properties);
        assertTrue(properties.isEnabled());
        assertEquals("BLOCK", properties.getActiveStrategy());

        // Interceptors
        assertNotNull(properties.getInterceptors());
        assertTrue(properties.getInterceptors().getMybatis().isEnabled());
        assertTrue(properties.getInterceptors().getMybatisPlus().isEnabled());
        assertTrue(properties.getInterceptors().getJdbc().isEnabled());

        // Deduplication
        assertNotNull(properties.getDeduplication());
        assertTrue(properties.getDeduplication().isEnabled());
        assertEquals(2000, properties.getDeduplication().getCacheSize());
        assertEquals(200, properties.getDeduplication().getTtlMs());

        // Rules
        assertNotNull(properties.getRules());
        assertNotNull(properties.getRules().getNoWhereClause());
        assertTrue(properties.getRules().getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.CRITICAL, properties.getRules().getNoWhereClause().getRiskLevel());
    }

    @Test
    public void testDefaults_shouldMatchSqlGuardConfigDefaults() {
        // Test default values match expected defaults
        SqlGuardProperties defaultProps = new SqlGuardProperties();
        
        assertTrue(defaultProps.isEnabled());
        assertEquals("LOG", defaultProps.getActiveStrategy());
        
        // Deduplication defaults
        assertTrue(defaultProps.getDeduplication().isEnabled());
        assertEquals(1000, defaultProps.getDeduplication().getCacheSize());
        assertEquals(100, defaultProps.getDeduplication().getTtlMs());
        
        // Rule defaults
        assertTrue(defaultProps.getRules().getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.CRITICAL, defaultProps.getRules().getNoWhereClause().getRiskLevel());
        
        assertTrue(defaultProps.getRules().getDummyCondition().isEnabled());
        assertEquals(RiskLevel.HIGH, defaultProps.getRules().getDummyCondition().getRiskLevel());
        
        assertTrue(defaultProps.getRules().getDeepPagination().isEnabled());
        assertEquals(RiskLevel.MEDIUM, defaultProps.getRules().getDeepPagination().getRiskLevel());
        assertEquals(10000, defaultProps.getRules().getDeepPagination().getMaxOffset());
        
        assertTrue(defaultProps.getRules().getLargePageSize().isEnabled());
        assertEquals(RiskLevel.MEDIUM, defaultProps.getRules().getLargePageSize().getRiskLevel());
        assertEquals(1000, defaultProps.getRules().getLargePageSize().getMaxPageSize());
    }

    @Test
    public void testEnabled_shouldBindCorrectly() {
        // Test that enabled property is bound correctly from test properties
        assertTrue(properties.isEnabled());
        
        // Test setter works on new instance (don't modify injected bean)
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.setEnabled(false);
        assertFalse(testProps.isEnabled());
    }

    @Test
    public void testActiveStrategy_shouldBindCorrectly() {
        // Test that activeStrategy is bound correctly from test properties
        assertEquals("BLOCK", properties.getActiveStrategy());
        
        // Test setters work on new instance (don't modify injected bean)
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.setActiveStrategy("LOG");
        assertEquals("LOG", testProps.getActiveStrategy());
        
        testProps.setActiveStrategy("WARN");
        assertEquals("WARN", testProps.getActiveStrategy());
    }

    @Test
    public void testNestedInterceptors_shouldBindCorrectly() {
        SqlGuardProperties.InterceptorsConfig interceptors = properties.getInterceptors();
        assertNotNull(interceptors);
        
        assertTrue(interceptors.getMybatis().isEnabled());
        assertTrue(interceptors.getMybatisPlus().isEnabled());
        assertTrue(interceptors.getJdbc().isEnabled());
        
        // Test setters on new instance (don't modify injected bean)
        SqlGuardProperties.MyBatisConfig testConfig = new SqlGuardProperties.MyBatisConfig();
        testConfig.setEnabled(false);
        assertFalse(testConfig.isEnabled());
    }

    @Test
    public void testNestedDeduplication_shouldBindCorrectly() {
        SqlGuardProperties.DeduplicationConfig dedup = properties.getDeduplication();
        assertNotNull(dedup);
        
        assertTrue(dedup.isEnabled());
        assertEquals(2000, dedup.getCacheSize());
        assertEquals(200, dedup.getTtlMs());
        
        // Test setters on new instance (don't modify injected bean)
        SqlGuardProperties.DeduplicationConfig testConfig = new SqlGuardProperties.DeduplicationConfig();
        testConfig.setCacheSize(5000);
        assertEquals(5000, testConfig.getCacheSize());
        
        testConfig.setTtlMs(500);
        assertEquals(500, testConfig.getTtlMs());
    }

    @Test
    public void testNestedRules_shouldBindCorrectly() {
        SqlGuardProperties.RulesConfig rules = properties.getRules();
        assertNotNull(rules);
        
        assertNotNull(rules.getNoWhereClause());
        assertNotNull(rules.getDummyCondition());
        assertNotNull(rules.getBlacklistFields());
        assertNotNull(rules.getWhitelistFields());
        assertNotNull(rules.getLogicalPagination());
        assertNotNull(rules.getNoConditionPagination());
        assertNotNull(rules.getDeepPagination());
        assertNotNull(rules.getLargePageSize());
        assertNotNull(rules.getMissingOrderBy());
        assertNotNull(rules.getNoPagination());
    }

    @Test
    public void testParserConfig_shouldBindCorrectly() {
        SqlGuardProperties.ParserConfig parser = properties.getParser();
        assertNotNull(parser);
        
        // Default should be false
        assertFalse(parser.isLenientMode());
        
        // Test setter on new instance (don't modify injected bean)
        SqlGuardProperties.ParserConfig testConfig = new SqlGuardProperties.ParserConfig();
        testConfig.setLenientMode(true);
        assertTrue(testConfig.isLenientMode());
    }

    @Test
    public void testGettersSetters_shouldWork() {
        SqlGuardProperties props = new SqlGuardProperties();
        
        // Test enabled
        props.setEnabled(false);
        assertFalse(props.isEnabled());
        
        // Test activeStrategy
        props.setActiveStrategy("WARN");
        assertEquals("WARN", props.getActiveStrategy());
        
        // Test nested objects
        SqlGuardProperties.InterceptorsConfig interceptors = new SqlGuardProperties.InterceptorsConfig();
        props.setInterceptors(interceptors);
        assertEquals(interceptors, props.getInterceptors());
        
        SqlGuardProperties.DeduplicationConfig dedup = new SqlGuardProperties.DeduplicationConfig();
        props.setDeduplication(dedup);
        assertEquals(dedup, props.getDeduplication());
        
        SqlGuardProperties.RulesConfig rules = new SqlGuardProperties.RulesConfig();
        props.setRules(rules);
        assertEquals(rules, props.getRules());
        
        SqlGuardProperties.ParserConfig parser = new SqlGuardProperties.ParserConfig();
        props.setParser(parser);
        assertEquals(parser, props.getParser());
    }

    @Test
    public void testToString_shouldIncludeAllFields() {
        String str = properties.toString();
        assertNotNull(str);
        assertTrue(str.contains("SqlGuardProperties"));
        assertTrue(str.contains("enabled="));
        assertTrue(str.contains("activeStrategy="));
        assertTrue(str.contains("interceptors="));
        assertTrue(str.contains("deduplication="));
        assertTrue(str.contains("rules="));
        assertTrue(str.contains("parser="));
    }

    /**
     * Test configuration to enable ConfigurationProperties.
     */
    @org.springframework.context.annotation.Configuration
    @EnableConfigurationProperties(SqlGuardProperties.class)
    static class TestConfig {
    }
}
