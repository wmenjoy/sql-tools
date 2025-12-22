package com.footstone.sqlguard.spring.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for nested configuration properties binding.
 */
@SpringBootTest(
    classes = NestedPropertiesTest.TestConfig.class,
    properties = {
        "sql-guard.interceptors.mybatis.enabled=true",
        "sql-guard.interceptors.mybatis-plus.enabled=false",
        "sql-guard.interceptors.jdbc.enabled=true",
        "sql-guard.deduplication.enabled=true",
        "sql-guard.deduplication.cache-size=5000",
        "sql-guard.deduplication.ttl-ms=500",
        "sql-guard.rules.no-where-clause.enabled=true",
        "sql-guard.rules.no-where-clause.risk-level=CRITICAL",
        "sql-guard.rules.dummy-condition.enabled=true",
        "sql-guard.rules.dummy-condition.risk-level=HIGH",
        "sql-guard.rules.dummy-condition.patterns[0]=1=1",
        "sql-guard.rules.dummy-condition.patterns[1]=true",
        "sql-guard.rules.deep-pagination.enabled=true",
        "sql-guard.rules.deep-pagination.max-offset=50000",
        "sql-guard.rules.large-page-size.enabled=true",
        "sql-guard.rules.large-page-size.max-page-size=2000",
        "sql-guard.parser.lenient-mode=true"
    }
)
public class NestedPropertiesTest {

    @Autowired
    private SqlGuardProperties properties;

    @Test
    public void testInterceptorsConfig_shouldBindNested() {
        SqlGuardProperties.InterceptorsConfig interceptors = properties.getInterceptors();
        assertNotNull(interceptors);
        assertNotNull(interceptors.getMybatis());
        assertNotNull(interceptors.getMybatisPlus());
        assertNotNull(interceptors.getJdbc());
    }

    @Test
    public void testMyBatisConfig_shouldBindCorrectly() {
        SqlGuardProperties.MyBatisConfig mybatis = properties.getInterceptors().getMybatis();
        assertNotNull(mybatis);
        assertTrue(mybatis.isEnabled());
    }

    @Test
    public void testMyBatisPlusConfig_shouldBindCorrectly() {
        SqlGuardProperties.MyBatisPlusConfig mybatisPlus = properties.getInterceptors().getMybatisPlus();
        assertNotNull(mybatisPlus);
        assertFalse(mybatisPlus.isEnabled()); // Set to false in test properties
    }

    @Test
    public void testJdbcConfig_shouldBindCorrectly() {
        SqlGuardProperties.JdbcConfig jdbc = properties.getInterceptors().getJdbc();
        assertNotNull(jdbc);
        assertTrue(jdbc.isEnabled());
    }

    @Test
    public void testDeduplicationConfig_shouldBindNested() {
        SqlGuardProperties.DeduplicationConfig dedup = properties.getDeduplication();
        assertNotNull(dedup);
        assertTrue(dedup.isEnabled());
        assertEquals(5000, dedup.getCacheSize());
        assertEquals(500, dedup.getTtlMs());
    }

    @Test
    public void testRulesConfig_shouldBindNested() {
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
    public void testAllRuleProperties_shouldBindCorrectly() {
        SqlGuardProperties.RulesConfig rules = properties.getRules();
        
        // NoWhereClause
        assertTrue(rules.getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.CRITICAL, rules.getNoWhereClause().getRiskLevel());
        
        // DummyCondition
        assertTrue(rules.getDummyCondition().isEnabled());
        assertEquals(RiskLevel.HIGH, rules.getDummyCondition().getRiskLevel());
        
        // DeepPagination
        assertTrue(rules.getDeepPagination().isEnabled());
        assertEquals(50000, rules.getDeepPagination().getMaxOffset());
        
        // LargePageSize
        assertTrue(rules.getLargePageSize().isEnabled());
        assertEquals(2000, rules.getLargePageSize().getMaxPageSize());
    }

    @Test
    public void testNoWhereClauseProperties_shouldHaveCorrectDefaults() {
        SqlGuardProperties.NoWhereClauseProperties props = new SqlGuardProperties.NoWhereClauseProperties();
        assertTrue(props.isEnabled());
        assertEquals(RiskLevel.CRITICAL, props.getRiskLevel());
    }

    @Test
    public void testDeepPaginationProperties_maxOffset_shouldBind() {
        SqlGuardProperties.DeepPaginationProperties props = properties.getRules().getDeepPagination();
        assertEquals(50000, props.getMaxOffset());
        
        // Test setter
        SqlGuardProperties.DeepPaginationProperties testProps = new SqlGuardProperties.DeepPaginationProperties();
        testProps.setMaxOffset(100000);
        assertEquals(100000, testProps.getMaxOffset());
    }

    @Test
    public void testLargePageSizeProperties_maxPageSize_shouldBind() {
        SqlGuardProperties.LargePageSizeProperties props = properties.getRules().getLargePageSize();
        assertEquals(2000, props.getMaxPageSize());
        
        // Test setter
        SqlGuardProperties.LargePageSizeProperties testProps = new SqlGuardProperties.LargePageSizeProperties();
        testProps.setMaxPageSize(5000);
        assertEquals(5000, testProps.getMaxPageSize());
    }

    @Test
    public void testWhitelistFieldProperties_map_shouldBind() {
        SqlGuardProperties.WhitelistFieldProperties props = properties.getRules().getWhitelistFields();
        assertNotNull(props);
        assertNotNull(props.getWhitelistFields());
        
        // Test with programmatic configuration
        Map<String, List<String>> whitelist = new HashMap<>();
        whitelist.put("user", Arrays.asList("id", "user_id"));
        whitelist.put("order", Arrays.asList("id", "order_id"));
        
        props.setWhitelistFields(whitelist);
        assertEquals(2, props.getWhitelistFields().size());
        assertTrue(props.getWhitelistFields().containsKey("user"));
        assertEquals(2, props.getWhitelistFields().get("user").size());
    }

    @Test
    public void testParserConfig_shouldBindCorrectly() {
        SqlGuardProperties.ParserConfig parser = properties.getParser();
        assertNotNull(parser);
        assertTrue(parser.isLenientMode()); // Set to true in test properties
    }

    /**
     * Test configuration to enable ConfigurationProperties.
     */
    @org.springframework.context.annotation.Configuration
    @EnableConfigurationProperties(SqlGuardProperties.class)
    static class TestConfig {
    }
}









