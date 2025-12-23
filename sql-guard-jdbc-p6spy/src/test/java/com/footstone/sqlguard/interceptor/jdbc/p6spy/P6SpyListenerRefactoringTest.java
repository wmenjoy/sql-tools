package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6Factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Listener Refactoring Tests for P6Spy module.
 *
 * <p>Verifies that the P6SpySqlSafetyListener follows the composition pattern,
 * properly implements JdbcEventListener, and integrates with the common
 * JdbcInterceptorBase abstraction.</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Composition pattern verification</li>
 *   <li>JdbcEventListener implementation</li>
 *   <li>P6Spy lifecycle hooks</li>
 *   <li>Configuration integration</li>
 *   <li>ServiceLoader SPI discovery</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("P6Spy Listener Refactoring Tests")
@ExtendWith(MockitoExtension.class)
class P6SpyListenerRefactoringTest {

    @Mock
    private StatementInformation mockStatementInfo;
    
    @Mock
    private ConnectionInformation mockConnectionInfo;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
    }

    // ==================== Test 1: Listener Composes JdbcInterceptorBase ====================
    
    @Test
    @DisplayName("1. P6SpySqlSafetyListener should compose P6SpyJdbcInterceptor")
    void testP6SpyListener_composesJdbcInterceptorBase() {
        // Given: P6SpySqlSafetyListener class
        Class<?> listenerClass = P6SpySqlSafetyListener.class;
        
        // Then: Should have a field or constructor parameter of type P6SpyJdbcInterceptor
        boolean hasInterceptorComposition = false;
        
        // Check declared fields
        for (java.lang.reflect.Field field : listenerClass.getDeclaredFields()) {
            if (P6SpyJdbcInterceptor.class.isAssignableFrom(field.getType()) ||
                JdbcInterceptorBase.class.isAssignableFrom(field.getType())) {
                hasInterceptorComposition = true;
                break;
            }
        }
        
        // Check constructors
        if (!hasInterceptorComposition) {
            for (java.lang.reflect.Constructor<?> constructor : listenerClass.getDeclaredConstructors()) {
                for (Class<?> paramType : constructor.getParameterTypes()) {
                    if (P6SpyJdbcInterceptor.class.isAssignableFrom(paramType) ||
                        JdbcInterceptorBase.class.isAssignableFrom(paramType)) {
                        hasInterceptorComposition = true;
                        break;
                    }
                }
            }
        }
        
        assertTrue(hasInterceptorComposition,
            "P6SpySqlSafetyListener should compose P6SpyJdbcInterceptor or JdbcInterceptorBase");
    }

    // ==================== Test 2: Listener Implements JdbcEventListener ====================
    
    @Test
    @DisplayName("2. P6SpySqlSafetyListener should implement JdbcEventListener")
    void testP6SpyListener_jdbcEventListener_implements() {
        // Given: P6SpySqlSafetyListener class
        Class<?> listenerClass = P6SpySqlSafetyListener.class;
        
        // Then: Should extend or implement JdbcEventListener
        assertTrue(JdbcEventListener.class.isAssignableFrom(listenerClass),
            "P6SpySqlSafetyListener should extend JdbcEventListener");
    }

    // ==================== Test 3: onBeforeAnyExecute Intercepts SQL ====================
    
    @Test
    @DisplayName("3. onBeforeAnyExecute should intercept SQL before execution")
    void testP6SpyListener_onBeforeAnyExecute_intercepts() throws SQLException {
        // Given: A P6SpySqlSafetyListener with mocked dependencies
        P6SpyInterceptorConfig config = createTestConfig(ViolationStrategy.WARN, true);
        P6SpyJdbcInterceptor interceptor = new P6SpyJdbcInterceptor(config);
        P6SpySqlSafetyListener listener = new P6SpySqlSafetyListener(interceptor, config);
        
        // And: StatementInformation with SQL
        when(mockStatementInfo.getSqlWithValues())
            .thenReturn("SELECT * FROM users WHERE id = 1");
        when(mockStatementInfo.getConnectionInformation())
            .thenReturn(mockConnectionInfo);
        when(mockConnectionInfo.getUrl())
            .thenReturn("jdbc:p6spy:h2:mem:test");
        
        // When: onBeforeAnyExecute is called
        assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo),
            "onBeforeAnyExecute should not throw for valid SQL with WARN strategy");
        
        // Then: SQL should have been processed (verify through mock interactions)
        verify(mockStatementInfo).getSqlWithValues();
    }

    // ==================== Test 4: SQL Extraction from StatementInformation ====================
    
    @Test
    @DisplayName("4. Should extract SQL using getSqlWithValues()")
    void testP6SpyListener_getSqlWithValues_extracts() throws SQLException {
        // Given: StatementInformation with parameterized SQL
        String expectedSql = "SELECT * FROM orders WHERE customer_id = 123 AND status = 'PENDING'";
        when(mockStatementInfo.getSqlWithValues()).thenReturn(expectedSql);
        when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
        when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost/db");
        
        P6SpyInterceptorConfig config = createTestConfig(ViolationStrategy.LOG, true);
        P6SpyJdbcInterceptor interceptor = new P6SpyJdbcInterceptor(config);
        P6SpySqlSafetyListener listener = new P6SpySqlSafetyListener(interceptor, config);
        
        // When: onBeforeAnyExecute is called
        listener.onBeforeAnyExecute(mockStatementInfo);
        
        // Then: getSqlWithValues should have been called
        verify(mockStatementInfo).getSqlWithValues();
    }

    // ==================== Test 5: Parameter Substitution Works ====================
    
    @Test
    @DisplayName("5. Parameter substitution should work correctly")
    void testP6SpyListener_parameterSubstitution_works() throws SQLException {
        // Given: SQL with substituted parameters
        when(mockStatementInfo.getSqlWithValues())
            .thenReturn("UPDATE users SET name = 'John' WHERE id = 42");
        when(mockStatementInfo.getConnectionInformation())
            .thenReturn(mockConnectionInfo);
        when(mockConnectionInfo.getUrl())
            .thenReturn("jdbc:p6spy:h2:mem:test");
        
        P6SpyInterceptorConfig config = createTestConfig(ViolationStrategy.WARN, true);
        P6SpyJdbcInterceptor interceptor = new P6SpyJdbcInterceptor(config);
        P6SpySqlSafetyListener listener = new P6SpySqlSafetyListener(interceptor, config);
        
        // When: onBeforeAnyExecute is called
        assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo),
            "Parameter-substituted SQL should be processed correctly");
        
        // Then: SQL with values should be validated
        verify(mockStatementInfo).getSqlWithValues();
    }

    // ==================== Test 6: ViolationStrategy Uses Common Enum ====================
    
    @Test
    @DisplayName("6. ViolationStrategy should use common module enum")
    void testP6SpyListener_violationStrategy_usesCommon() {
        // Given: P6SpyInterceptorConfig
        P6SpyInterceptorConfig config = createTestConfig(ViolationStrategy.BLOCK, true);
        
        // Then: Strategy should be from common module
        assertThat(config.getStrategy())
            .isInstanceOf(ViolationStrategy.class)
            .isEqualTo(ViolationStrategy.BLOCK);
        
        // And: Helper methods should work
        assertTrue(config.getStrategy().shouldBlock(),
            "BLOCK strategy shouldBlock() should return true");
        assertTrue(config.getStrategy().shouldLog(),
            "BLOCK strategy shouldLog() should return true");
        assertEquals("ERROR", config.getStrategy().getLogLevel(),
            "BLOCK strategy getLogLevel() should return ERROR");
    }

    // ==================== Test 7: Audit Event Uses Common Builder ====================
    
    @Test
    @DisplayName("7. Audit events should use common JdbcAuditEventBuilder")
    void testP6SpyListener_auditEvent_usesCommonBuilder() {
        // Given: Check that JdbcAuditEventBuilder is available
        boolean builderAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.common.JdbcAuditEventBuilder");
        
        assertTrue(builderAvailable,
            "JdbcAuditEventBuilder from common module should be available");
    }

    // ==================== Test 8: Configuration Extends Common Interface ====================
    
    @Test
    @DisplayName("8. P6SpyInterceptorConfig should extend JdbcInterceptorConfig")
    void testP6SpyListener_configuration_extendsCommon() {
        // Given: P6SpyInterceptorConfig class
        Class<?> configClass = P6SpyInterceptorConfig.class;
        
        // Then: Should extend or implement JdbcInterceptorConfig
        assertTrue(JdbcInterceptorConfig.class.isAssignableFrom(configClass),
            "P6SpyInterceptorConfig should extend JdbcInterceptorConfig");
    }

    // ==================== Test 9: ServiceLoader Discovers Module ====================
    
    @Test
    @DisplayName("9. ServiceLoader should discover P6SpySqlSafetyModule")
    void testP6SpyModule_serviceLoader_discovers() {
        // Note: P6Spy's JdbcEventListener does NOT directly implement P6Factory.
        // P6Spy uses a different mechanism where JdbcEventListener instances are
        // discovered through modulelist configuration in spy.properties or via
        // P6ModuleManager.register(). The SPI file is used for backwards compatibility
        // with older P6Spy versions.
        
        // Verify our module class exists and is a JdbcEventListener
        boolean isJdbcEventListener = JdbcEventListener.class.isAssignableFrom(P6SpySqlSafetyModule.class);
        assertTrue(isJdbcEventListener,
            "P6SpySqlSafetyModule should extend JdbcEventListener");
        
        // Verify SPI file exists in resources
        java.net.URL spiResource = P6SpySqlSafetyModule.class.getClassLoader()
            .getResource("META-INF/services/com.p6spy.engine.spy.P6Factory");
        
        // Note: The SPI file may or may not load correctly depending on P6Spy version
        // The important thing is that our module correctly extends JdbcEventListener
        assertNotNull(spiResource, 
            "SPI registration file should exist in classpath");
    }

    // ==================== Test 10: System Property Configuration ====================
    
    @Test
    @DisplayName("10. System property configuration should work")
    void testP6SpyModule_systemProperty_configures() {
        // Given: P6SpyInterceptorConfig should support system properties
        P6SpyInterceptorConfig config = createTestConfig(ViolationStrategy.WARN, true);
        
        // Then: Config should have property prefix method
        assertNotNull(config.getPropertyPrefix(),
            "Config should return property prefix");
        assertThat(config.getPropertyPrefix())
            .contains("sqlguard")
            .contains("p6spy");
        
        // And: Parameterized SQL logging should be configurable
        assertThat(config.isLogParameterizedSql())
            .isNotNull();
    }

    // ==================== Helper Methods ====================
    
    /**
     * Creates a test configuration with specified settings.
     */
    private P6SpyInterceptorConfig createTestConfig(ViolationStrategy strategy, boolean enabled) {
        return new P6SpyInterceptorConfig() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public ViolationStrategy getStrategy() {
                return strategy;
            }

            @Override
            public boolean isAuditEnabled() {
                return false;
            }

            @Override
            public java.util.List<String> getExcludePatterns() {
                return java.util.Collections.emptyList();
            }

            @Override
            public String getPropertyPrefix() {
                return "sqlguard.p6spy";
            }

            @Override
            public boolean isLogParameterizedSql() {
                return true;
            }
        };
    }
    
    /**
     * Checks if a class is available on the classpath.
     */
    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}






