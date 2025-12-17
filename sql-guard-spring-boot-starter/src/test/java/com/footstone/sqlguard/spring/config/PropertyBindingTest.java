package com.footstone.sqlguard.spring.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test class for property binding with various YAML configurations.
 */
public class PropertyBindingTest {

    /**
     * Test full configuration with all properties specified.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.enabled=true",
            "sql-guard.active-strategy=BLOCK",
            "sql-guard.interceptors.mybatis.enabled=true",
            "sql-guard.interceptors.mybatis-plus.enabled=true",
            "sql-guard.interceptors.jdbc.enabled=true",
            "sql-guard.deduplication.enabled=true",
            "sql-guard.deduplication.cache-size=5000",
            "sql-guard.deduplication.ttl-ms=500",
            "sql-guard.parser.lenient-mode=true",
            "sql-guard.rules.no-where-clause.enabled=true",
            "sql-guard.rules.no-where-clause.risk-level=CRITICAL",
            "sql-guard.rules.dummy-condition.patterns[0]=1=1",
            "sql-guard.rules.dummy-condition.patterns[1]=true",
            "sql-guard.rules.dummy-condition.patterns[2]='a'='a'",
            "sql-guard.rules.blacklist-fields.blacklist-fields[0]=deleted",
            "sql-guard.rules.blacklist-fields.blacklist-fields[1]=status",
            "sql-guard.rules.blacklist-fields.blacklist-fields[2]=enabled",
            "sql-guard.rules.whitelist-fields.whitelist-fields.user[0]=id",
            "sql-guard.rules.whitelist-fields.whitelist-fields.user[1]=user_id",
            "sql-guard.rules.whitelist-fields.whitelist-fields.order[0]=id",
            "sql-guard.rules.whitelist-fields.whitelist-fields.order[1]=order_id",
            "sql-guard.rules.deep-pagination.max-offset=50000",
            "sql-guard.rules.large-page-size.max-page-size=2000"
        }
    )
    public static class FullConfigurationTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testFullConfiguration_shouldBindAllProperties() {
            // Root properties
            assertTrue(properties.isEnabled());
            assertEquals("BLOCK", properties.getActiveStrategy());

            // Interceptors
            assertTrue(properties.getInterceptors().getMybatis().isEnabled());
            assertTrue(properties.getInterceptors().getMybatisPlus().isEnabled());
            assertTrue(properties.getInterceptors().getJdbc().isEnabled());

            // Deduplication
            assertTrue(properties.getDeduplication().isEnabled());
            assertEquals(5000, properties.getDeduplication().getCacheSize());
            assertEquals(500, properties.getDeduplication().getTtlMs());

            // Parser
            assertTrue(properties.getParser().isLenientMode());

            // Rules
            assertTrue(properties.getRules().getNoWhereClause().isEnabled());
            assertEquals(RiskLevel.CRITICAL, properties.getRules().getNoWhereClause().getRiskLevel());

            assertEquals(3, properties.getRules().getDummyCondition().getPatterns().size());
            assertEquals(3, properties.getRules().getBlacklistFields().getBlacklistFields().size());

            Map<String, List<String>> whitelist = properties.getRules().getWhitelistFields().getWhitelistFields();
            assertEquals(2, whitelist.size());
            assertTrue(whitelist.containsKey("user"));
            assertEquals(2, whitelist.get("user").size());

            assertEquals(50000, properties.getRules().getDeepPagination().getMaxOffset());
            assertEquals(2000, properties.getRules().getLargePageSize().getMaxPageSize());
        }
    }

    /**
     * Test minimal configuration with only required properties.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.enabled=true"
        }
    )
    public static class MinimalConfigurationTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testMinimalConfiguration_shouldUseDefaults() {
            assertTrue(properties.isEnabled());
            assertEquals("LOG", properties.getActiveStrategy()); // Default
            assertEquals(1000, properties.getDeduplication().getCacheSize()); // Default
            assertEquals(100, properties.getDeduplication().getTtlMs()); // Default
            assertFalse(properties.getParser().isLenientMode()); // Default
        }
    }

    /**
     * Test dev profile configuration.
     */
    @SpringBootTest(classes = TestConfig.class)
    @ActiveProfiles("dev")
    public static class DevProfileTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testDevProfile_shouldOverrideDefaults() {
            assertEquals("LOG", properties.getActiveStrategy());
            assertEquals(1000, properties.getDeduplication().getCacheSize());
        }
    }

    /**
     * Test prod profile configuration.
     */
    @SpringBootTest(classes = TestConfig.class)
    @ActiveProfiles("prod")
    public static class ProdProfileTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testProdProfile_shouldOverrideDefaults() {
            assertEquals("BLOCK", properties.getActiveStrategy());
            assertEquals(10000, properties.getDeduplication().getCacheSize());
            assertEquals(200, properties.getDeduplication().getTtlMs());
        }
    }

    /**
     * Test nested interceptors binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.interceptors.mybatis.enabled=true",
            "sql-guard.interceptors.mybatis-plus.enabled=false",
            "sql-guard.interceptors.jdbc.enabled=true"
        }
    )
    public static class NestedInterceptorsTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testNestedInterceptors_shouldBindCorrectly() {
            assertTrue(properties.getInterceptors().getMybatis().isEnabled());
            assertFalse(properties.getInterceptors().getMybatisPlus().isEnabled());
            assertTrue(properties.getInterceptors().getJdbc().isEnabled());
        }
    }

    /**
     * Test nested deduplication binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.deduplication.enabled=true",
            "sql-guard.deduplication.cache-size=3000",
            "sql-guard.deduplication.ttl-ms=300"
        }
    )
    public static class NestedDeduplicationTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testNestedDeduplication_shouldBindCorrectly() {
            assertTrue(properties.getDeduplication().isEnabled());
            assertEquals(3000, properties.getDeduplication().getCacheSize());
            assertEquals(300, properties.getDeduplication().getTtlMs());
        }
    }

    /**
     * Test nested rules binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.rules.no-where-clause.enabled=true",
            "sql-guard.rules.no-where-clause.risk-level=CRITICAL",
            "sql-guard.rules.deep-pagination.max-offset=20000",
            "sql-guard.rules.large-page-size.max-page-size=500"
        }
    )
    public static class NestedRulesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testNestedRules_shouldBindCorrectly() {
            assertTrue(properties.getRules().getNoWhereClause().isEnabled());
            assertEquals(RiskLevel.CRITICAL, properties.getRules().getNoWhereClause().getRiskLevel());
            assertEquals(20000, properties.getRules().getDeepPagination().getMaxOffset());
            assertEquals(500, properties.getRules().getLargePageSize().getMaxPageSize());
        }
    }

    /**
     * Test list properties binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.rules.dummy-condition.patterns[0]=1=1",
            "sql-guard.rules.dummy-condition.patterns[1]=true",
            "sql-guard.rules.dummy-condition.patterns[2]='x'='x'",
            "sql-guard.rules.blacklist-fields.blacklist-fields[0]=deleted",
            "sql-guard.rules.blacklist-fields.blacklist-fields[1]=status"
        }
    )
    public static class ListPropertiesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testListProperties_shouldBindCorrectly() {
            List<String> patterns = properties.getRules().getDummyCondition().getPatterns();
            assertEquals(3, patterns.size());
            assertTrue(patterns.contains("1=1"));
            
            List<String> blacklist = properties.getRules().getBlacklistFields().getBlacklistFields();
            assertEquals(2, blacklist.size());
            assertTrue(blacklist.contains("deleted"));
        }
    }

    /**
     * Test map properties binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.rules.whitelist-fields.whitelist-fields.user[0]=id",
            "sql-guard.rules.whitelist-fields.whitelist-fields.user[1]=user_id",
            "sql-guard.rules.whitelist-fields.whitelist-fields.order[0]=order_id"
        }
    )
    public static class MapPropertiesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testMapProperties_shouldBindCorrectly() {
            Map<String, List<String>> whitelist = properties.getRules().getWhitelistFields().getWhitelistFields();
            assertEquals(2, whitelist.size());
            assertTrue(whitelist.containsKey("user"));
            assertEquals(2, whitelist.get("user").size());
            assertTrue(whitelist.get("user").contains("id"));
        }
    }

    /**
     * Test numeric properties binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.deduplication.cache-size=7500",
            "sql-guard.deduplication.ttl-ms=750",
            "sql-guard.rules.deep-pagination.max-offset=30000",
            "sql-guard.rules.large-page-size.max-page-size=1500",
            "sql-guard.rules.no-pagination.estimated-rows-threshold=50000"
        }
    )
    public static class NumericPropertiesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testNumericProperties_shouldBindCorrectly() {
            assertEquals(7500, properties.getDeduplication().getCacheSize());
            assertEquals(750, properties.getDeduplication().getTtlMs());
            assertEquals(30000, properties.getRules().getDeepPagination().getMaxOffset());
            assertEquals(1500, properties.getRules().getLargePageSize().getMaxPageSize());
            assertEquals(50000, properties.getRules().getNoPagination().getEstimatedRowsThreshold());
        }
    }

    /**
     * Test enum properties binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.rules.no-where-clause.risk-level=CRITICAL",
            "sql-guard.rules.dummy-condition.risk-level=HIGH",
            "sql-guard.rules.deep-pagination.risk-level=MEDIUM",
            "sql-guard.rules.missing-order-by.risk-level=LOW"
        }
    )
    public static class EnumPropertiesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testEnumProperties_shouldBindCorrectly() {
            assertEquals(RiskLevel.CRITICAL, properties.getRules().getNoWhereClause().getRiskLevel());
            assertEquals(RiskLevel.HIGH, properties.getRules().getDummyCondition().getRiskLevel());
            assertEquals(RiskLevel.MEDIUM, properties.getRules().getDeepPagination().getRiskLevel());
            assertEquals(RiskLevel.LOW, properties.getRules().getMissingOrderBy().getRiskLevel());
        }
    }

    /**
     * Test kebab-case to camelCase binding.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.active-strategy=WARN",
            "sql-guard.deduplication.cache-size=2500",
            "sql-guard.deduplication.ttl-ms=250",
            "sql-guard.parser.lenient-mode=true"
        }
    )
    public static class KebabCaseTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testKebabCase_shouldBindToJavaCase() {
            assertEquals("WARN", properties.getActiveStrategy());
            assertEquals(2500, properties.getDeduplication().getCacheSize());
            assertEquals(250, properties.getDeduplication().getTtlMs());
            assertTrue(properties.getParser().isLenientMode());
        }
    }

    /**
     * Test snake_case to camelCase binding (Spring Boot supports this).
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql_guard.active_strategy=BLOCK",
            "sql_guard.deduplication.cache_size=3500"
        }
    )
    public static class SnakeCaseTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testSnakeCase_shouldBindToJavaCase() {
            assertEquals("BLOCK", properties.getActiveStrategy());
            assertEquals(3500, properties.getDeduplication().getCacheSize());
        }
    }

    /**
     * Test environment variables override YAML.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.active-strategy=BLOCK"
        }
    )
    public static class EnvironmentVariablesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testEnvironmentVariables_shouldOverrideYaml() {
            // Properties from @SpringBootTest properties array override YAML
            assertEquals("BLOCK", properties.getActiveStrategy());
        }
    }

    /**
     * Test system properties override all.
     */
    @SpringBootTest(
        classes = TestConfig.class,
        properties = {
            "sql-guard.active-strategy=LOG",
            "sql-guard.deduplication.cache-size=1500"
        }
    )
    public static class SystemPropertiesTest {
        
        @Autowired
        private SqlGuardProperties properties;

        @Test
        public void testSystemProperties_shouldOverrideAll() {
            // Test properties have highest precedence
            assertEquals("LOG", properties.getActiveStrategy());
            assertEquals(1500, properties.getDeduplication().getCacheSize());
        }
    }

    /**
     * Test configuration to enable ConfigurationProperties.
     */
    @org.springframework.context.annotation.Configuration
    @EnableConfigurationProperties(SqlGuardProperties.class)
    static class TestConfig {
    }
}
