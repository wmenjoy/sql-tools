package com.footstone.sqlguard.interceptor.jdbc.druid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.CallableStatementProxy;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.PreparedStatementProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Filter Refactoring Tests for sql-guard-jdbc-druid.
 *
 * <p>Verifies that DruidSqlSafetyFilter is properly refactored to compose
 * DruidJdbcInterceptor (which extends JdbcInterceptorBase), ensuring consistent
 * behavior through the template method pattern.</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Composition pattern verification</li>
 *   <li>Template method delegation</li>
 *   <li>FilterAdapter integration</li>
 *   <li>Connection/Statement proxy interception</li>
 *   <li>ViolationStrategy usage from common module</li>
 * </ul>
 *
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Druid Filter Refactoring Tests")
class DruidFilterRefactoringTest {

    @Mock
    private DefaultSqlSafetyValidator validator;
    
    @Mock
    private ConnectionProxy connectionProxy;
    
    @Mock
    private DataSourceProxy dataSourceProxy;
    
    @Mock
    private FilterChain filterChain;
    
    @Mock
    private StatementProxy statementProxy;
    
    @Mock
    private PreparedStatementProxy preparedStatementProxy;

    @BeforeEach
    void setUp() {
        // Setup common mock behavior
        lenient().when(connectionProxy.getDirectDataSource()).thenReturn(dataSourceProxy);
        lenient().when(dataSourceProxy.getName()).thenReturn("testDruidDataSource");
        lenient().when(statementProxy.getConnectionProxy()).thenReturn(connectionProxy);
        lenient().when(preparedStatementProxy.getConnectionProxy()).thenReturn(connectionProxy);
        
        // Clear any thread-local state
        com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
    }

    // ==================== Test 1: Filter Composes JdbcInterceptorBase ====================
    
    @Test
    @DisplayName("1. DruidSqlSafetyFilter should compose DruidJdbcInterceptor")
    void testDruidFilter_composesJdbcInterceptorBase() {
        // Given: Create filter with composition
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // Then: Filter should hold a reference to interceptor
        assertNotNull(filter, "Filter should be created with interceptor");
        
        // And: Interceptor should extend JdbcInterceptorBase
        assertTrue(interceptor instanceof JdbcInterceptorBase,
            "DruidJdbcInterceptor should extend JdbcInterceptorBase");
    }

    // ==================== Test 2: Template Method Delegates ====================
    
    @Test
    @DisplayName("2. Template method should delegate validation to interceptor")
    void testDruidFilter_templateMethod_delegates() throws SQLException {
        // Given: Setup validation
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // When: Execute interceptSql through filter
        String sql = "SELECT * FROM users WHERE id = 1";
        filter.connection_prepareStatement(filterChain, connectionProxy, sql);
        
        // Then: Validator should be called via interceptor
        verify(validator).validate(any(SqlContext.class));
    }

    // ==================== Test 3: FilterAdapter Integration ====================
    
    @Test
    @DisplayName("3. DruidSqlSafetyFilter should extend FilterAdapter")
    void testDruidFilter_filterAdapter_integrates() {
        // Given: Create filter
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // Then: Filter should extend FilterAdapter
        assertTrue(filter instanceof FilterAdapter,
            "DruidSqlSafetyFilter should extend FilterAdapter for Druid integration");
    }

    // ==================== Test 4: Connection Proxy Interception ====================
    
    @Test
    @DisplayName("4. Filter should intercept connection_prepareStatement")
    void testDruidFilter_connectionProxy_intercepts() throws SQLException {
        // Given: Setup validation
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // When: Call connection_prepareStatement
        String sql = "INSERT INTO users (name) VALUES ('test')";
        filter.connection_prepareStatement(filterChain, connectionProxy, sql);
        
        // Then: SQL should be validated
        ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
        verify(validator).validate(captor.capture());
        assertThat(captor.getValue().getSql()).isEqualTo(sql);
    }

    // ==================== Test 5: Statement Proxy Interception ====================
    
    @Test
    @DisplayName("5. Filter should intercept statement_executeQuery")
    void testDruidFilter_statementProxy_intercepts() throws SQLException {
        // Given: Setup validation
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // When: Call statement_executeQuery
        String sql = "SELECT * FROM orders WHERE user_id = 1";
        filter.statement_executeQuery(filterChain, statementProxy, sql);
        
        // Then: SQL should be validated
        ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
        verify(validator).validate(captor.capture());
        assertThat(captor.getValue().getSql()).isEqualTo(sql);
    }

    // ==================== Test 6: PreparedStatement Interception ====================
    
    @Test
    @DisplayName("6. Filter should intercept preparedStatement execution")
    void testDruidFilter_preparedStatement_intercepts() throws SQLException {
        // Given: Setup validation
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // When: Call connection_prepareStatement (validation happens at prepare time)
        String sql = "UPDATE products SET stock = stock - 1 WHERE id = ?";
        filter.connection_prepareStatement(filterChain, connectionProxy, sql);
        
        // Then: SQL should be validated at prepare time
        ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
        verify(validator).validate(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SqlCommandType.UPDATE);
    }

    // ==================== Test 7: CallableStatement Interception ====================
    
    @Test
    @DisplayName("7. Filter should intercept callableStatement creation")
    void testDruidFilter_callableStatement_intercepts() throws SQLException {
        // Given: Setup validation
        when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
        
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
        
        // When: Call connection_prepareCall
        String sql = "{call process_order(?)}";
        filter.connection_prepareCall(filterChain, connectionProxy, sql);
        
        // Then: SQL should be validated
        ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
        verify(validator).validate(captor.capture());
        assertThat(captor.getValue().getSql()).isEqualTo(sql);
    }

    // ==================== Test 8: ViolationStrategy Uses Common ====================
    
    @Test
    @DisplayName("8. Filter should use ViolationStrategy from common module")
    void testDruidFilter_violationStrategy_usesCommon() {
        // Given: Different strategies from common module
        ViolationStrategy blockStrategy = ViolationStrategy.BLOCK;
        ViolationStrategy warnStrategy = ViolationStrategy.WARN;
        ViolationStrategy logStrategy = ViolationStrategy.LOG;
        
        // Then: Strategies should have correct behavior
        assertAll("ViolationStrategy behavior",
            () -> assertTrue(blockStrategy.shouldBlock(), "BLOCK should block"),
            () -> assertFalse(warnStrategy.shouldBlock(), "WARN should not block"),
            () -> assertFalse(logStrategy.shouldBlock(), "LOG should not block"),
            () -> assertTrue(warnStrategy.shouldLog(), "WARN should log"),
            () -> assertTrue(logStrategy.shouldLog(), "LOG should log"),
            () -> assertEquals("ERROR", blockStrategy.getLogLevel(), "BLOCK log level should be ERROR"),
            () -> assertEquals("WARN", logStrategy.getLogLevel(), "LOG log level should be WARN")
        );
    }

    // ==================== Test 9: Audit Event Uses Common Builder ====================
    
    @Test
    @DisplayName("9. Filter should use JdbcAuditEventBuilder from common module")
    void testDruidFilter_auditEvent_usesCommonBuilder() {
        // Given: JdbcAuditEventBuilder should be available
        boolean builderAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.common.JdbcAuditEventBuilder");
        
        // Then: Builder class should be available from common module
        assertTrue(builderAvailable,
            "JdbcAuditEventBuilder should be available from common module");
    }

    // ==================== Test 10: Configuration Extends Common ====================
    
    @Test
    @DisplayName("10. DruidInterceptorConfig should extend JdbcInterceptorConfig")
    void testDruidFilter_configuration_extendsCommon() {
        // Given: Create Druid-specific config
        DruidInterceptorConfig config = createTestConfig(ViolationStrategy.WARN);
        
        // Then: Config should implement JdbcInterceptorConfig
        assertTrue(config instanceof JdbcInterceptorConfig,
            "DruidInterceptorConfig should implement JdbcInterceptorConfig");
        
        // And: Common methods should be available
        assertAll("JdbcInterceptorConfig methods",
            () -> assertNotNull(config.getStrategy(), "getStrategy should be available"),
            () -> assertNotNull(config.getExcludePatterns(), "getExcludePatterns should be available"),
            () -> assertDoesNotThrow(() -> config.isEnabled(), "isEnabled should be available"),
            () -> assertDoesNotThrow(() -> config.isAuditEnabled(), "isAuditEnabled should be available")
        );
        
        // And: Druid-specific methods should be available
        assertAll("DruidInterceptorConfig specific methods",
            () -> assertDoesNotThrow(() -> config.getFilterPosition(), 
                "getFilterPosition should be available"),
            () -> assertDoesNotThrow(() -> config.isConnectionProxyEnabled(), 
                "isConnectionProxyEnabled should be available")
        );
    }

    // ==================== Helper Methods ====================
    
    /**
     * Creates a test configuration with specified strategy.
     */
    private DruidInterceptorConfig createTestConfig(ViolationStrategy strategy) {
        return new DruidInterceptorConfig() {
            @Override
            public boolean isEnabled() {
                return true;
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
            public List<String> getExcludePatterns() {
                return Collections.emptyList();
            }
            
            @Override
            public int getFilterPosition() {
                return 0;
            }
            
            @Override
            public boolean isConnectionProxyEnabled() {
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

