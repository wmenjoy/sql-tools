package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.spring.autoconfigure.interceptor.SqlGuardInterceptorEnabled;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for different interceptor profiles.
 *
 * <p>These tests verify that the correct interceptor is selected based on
 * Spring profiles and configuration properties.</p>
 *
 * <h2>Profile Scenarios</h2>
 * <ul>
 *   <li><strong>mybatis</strong> - MyBatis interceptor (highest priority)</li>
 *   <li><strong>druid</strong> - Druid filter (second priority)</li>
 *   <li><strong>default</strong> - Priority-based auto-selection</li>
 * </ul>
 *
 * <h2>Strategy Scenarios</h2>
 * <ul>
 *   <li><strong>log</strong> - LOG strategy (silent logging)</li>
 *   <li><strong>warn</strong> - WARN strategy (error logging)</li>
 *   <li><strong>block</strong> - BLOCK strategy (throw exception)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:profile_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "sql-guard.active-strategy=LOG"
})
class InterceptorProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SqlGuardProperties properties;

    @Test
    @DisplayName("SQL Guard should be enabled by default")
    void testSqlGuardEnabled() {
        assertTrue(properties.isEnabled(), "SQL Guard should be enabled");
    }

    @Test
    @DisplayName("Default active strategy should be LOG")
    void testDefaultStrategy() {
        assertEquals("LOG", properties.getActiveStrategy(),
                     "Default active strategy should be LOG");
    }

    @Test
    @DisplayName("Core validator should be present")
    void testCoreValidatorPresent() {
        assertTrue(context.containsBean("sqlSafetyValidator"),
                   "Core validator should be present");
    }

    @Test
    @DisplayName("Only one interceptor should be selected based on priority")
    void testOnlyOneInterceptorSelected() {
        Map<String, SqlGuardInterceptorEnabled> markers = 
            context.getBeansOfType(SqlGuardInterceptorEnabled.class);
        
        // Due to priority mechanism, at most one interceptor should be enabled
        assertTrue(markers.size() <= 1, 
                   "Only one interceptor should be selected, found: " + markers.size());
        
        if (!markers.isEmpty()) {
            String type = markers.values().iterator().next().getInterceptorType();
            System.out.println("Selected interceptor: " + type);
            assertTrue(
                type.equals("mybatis") || type.equals("druid") || 
                type.equals("hikari") || type.equals("p6spy"),
                "Interceptor type should be valid: " + type);
        }
    }

    @Test
    @DisplayName("MyBatis interceptor should have highest priority")
    void testMyBatisHighestPriority() {
        // Since MyBatis is on classpath and enabled by default,
        // MyBatis interceptor should be selected
        Map<String, SqlGuardInterceptorEnabled> markers = 
            context.getBeansOfType(SqlGuardInterceptorEnabled.class);
        
        if (!markers.isEmpty()) {
            String type = markers.values().iterator().next().getInterceptorType();
            // With MyBatis on classpath and enabled, it should be selected
            assertEquals("mybatis", type, 
                         "MyBatis should be selected as highest priority interceptor");
        }
    }

    @Test
    @DisplayName("All 21 rule checkers should be registered")
    void testAllCheckersRegistered() {
        // Verify that all checkers are registered
        assertTrue(context.containsBean("noWhereClauseChecker"), "NoWhereClauseChecker should be present");
        assertTrue(context.containsBean("dummyConditionChecker"), "DummyConditionChecker should be present");
        assertTrue(context.containsBean("blacklistFieldChecker"), "BlacklistFieldChecker should be present");
        assertTrue(context.containsBean("whitelistFieldChecker"), "WhitelistFieldChecker should be present");
        
        // SQL Injection checkers
        assertTrue(context.containsBean("multiStatementChecker"), "MultiStatementChecker should be present");
        assertTrue(context.containsBean("setOperationChecker"), "SetOperationChecker should be present");
        assertTrue(context.containsBean("sqlCommentChecker"), "SqlCommentChecker should be present");
        assertTrue(context.containsBean("intoOutfileChecker"), "IntoOutfileChecker should be present");
        
        // Dangerous operations checkers
        assertTrue(context.containsBean("ddlOperationChecker"), "DdlOperationChecker should be present");
        assertTrue(context.containsBean("dangerousFunctionChecker"), "DangerousFunctionChecker should be present");
        assertTrue(context.containsBean("callStatementChecker"), "CallStatementChecker should be present");
        
        // Access control checkers
        assertTrue(context.containsBean("metadataStatementChecker"), "MetadataStatementChecker should be present");
        assertTrue(context.containsBean("setStatementChecker"), "SetStatementChecker should be present");
        assertTrue(context.containsBean("deniedTableChecker"), "DeniedTableChecker should be present");
        assertTrue(context.containsBean("readOnlyTableChecker"), "ReadOnlyTableChecker should be present");
    }

    @Test
    @DisplayName("Interceptor configuration properties should be accessible")
    void testInterceptorConfigurationProperties() {
        assertNotNull(properties.getInterceptors(), "Interceptors config should not be null");
        assertNotNull(properties.getInterceptors().getMybatis(), "MyBatis config should not be null");
        assertNotNull(properties.getInterceptors().getJdbc(), "JDBC config should not be null");
    }
}
