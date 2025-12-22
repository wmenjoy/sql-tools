package com.footstone.sqlguard.interceptor.jdbc.hikari;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase;
import com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.CallableStatement;
import java.util.Collections;
import java.util.List;

/**
 * Proxy Refactoring Tests for HikariCP Module.
 *
 * <p>Verifies that HikariCP interceptors are refactored to compose JdbcInterceptorBase
 * and use the unified ViolationStrategy from sql-guard-jdbc-common.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>Composition pattern verification</li>
 *   <li>Multi-layer proxy chain (DataSource → Connection → Statement)</li>
 *   <li>ViolationStrategy from common module</li>
 *   <li>Configuration extends common interface</li>
 *   <li>Audit event building using common builder</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("HikariCP Proxy Refactoring Tests")
@ExtendWith(MockitoExtension.class)
class HikariProxyRefactoringTest {

    @Mock
    private DefaultSqlSafetyValidator validator;

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    @Mock
    private Statement mockStatement;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private CallableStatement mockCallableStatement;

    @BeforeEach
    void setUp() throws SQLException {
        // Setup default mock behaviors
        lenient().when(validator.validate(any())).thenReturn(ValidationResult.pass());
        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
        lenient().when(mockDataSource.toString()).thenReturn("test-hikari-datasource");
    }

    /**
     * Test 1: Verify HikariJdbcInterceptor extends JdbcInterceptorBase.
     * <p>Composition pattern - HikariCP interceptor should use common base class.</p>
     */
    @Test
    @DisplayName("testHikariProxy_composesJdbcInterceptorBase")
    void testHikariProxy_composesJdbcInterceptorBase() {
        // Verify HikariJdbcInterceptor class exists and extends JdbcInterceptorBase
        try {
            Class<?> interceptorClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariJdbcInterceptor");
            
            assertThat(JdbcInterceptorBase.class.isAssignableFrom(interceptorClass))
                .as("HikariJdbcInterceptor should extend JdbcInterceptorBase")
                .isTrue();
        } catch (ClassNotFoundException e) {
            // Class not yet implemented - test documents expected behavior
            // This is expected in TDD - RED phase
            fail("HikariJdbcInterceptor class should exist and extend JdbcInterceptorBase");
        }
    }

    /**
     * Test 2: Verify DataSource proxy wraps original DataSource.
     * <p>Layer 1 of multi-layer proxy chain.</p>
     */
    @Test
    @DisplayName("testHikariProxy_dataSourceProxy_wraps")
    void testHikariProxy_dataSourceProxy_wraps() throws SQLException {
        // Given
        ViolationStrategy strategy = ViolationStrategy.WARN;

        try {
            // Try to use the new refactored factory
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            // Use reflection to call wrap method
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // Then - verify it's a JDK dynamic proxy
            assertThat(Proxy.isProxyClass(wrappedDs.getClass()))
                .as("Wrapped DataSource should be a JDK dynamic proxy")
                .isTrue();

            // Verify DataSource interface is proxied
            assertThat(wrappedDs).isInstanceOf(DataSource.class);

        } catch (ClassNotFoundException e) {
            // Factory not yet implemented - test documents expected behavior
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed to invoke wrap method: " + e.getMessage());
        }
    }

    /**
     * Test 3: Verify Connection proxy intercepts getConnection.
     * <p>Layer 2 - Connection returned from DataSource should be proxied.</p>
     */
    @Test
    @DisplayName("testHikariProxy_connectionProxy_intercepts")
    void testHikariProxy_connectionProxy_intercepts() throws SQLException {
        // Given
        ViolationStrategy strategy = ViolationStrategy.WARN;
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        try {
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // When - get connection
            Connection conn = wrappedDs.getConnection();

            // Then - connection should be proxied
            assertThat(Proxy.isProxyClass(conn.getClass()))
                .as("Connection should be a JDK dynamic proxy")
                .isTrue();
            assertThat(conn).isInstanceOf(Connection.class);

        } catch (ClassNotFoundException e) {
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed to invoke methods: " + e.getMessage());
        }
    }

    /**
     * Test 4: Verify Statement proxy intercepts createStatement.
     * <p>Layer 3 - Statement created from Connection should be proxied.</p>
     */
    @Test
    @DisplayName("testHikariProxy_statementProxy_intercepts")
    void testHikariProxy_statementProxy_intercepts() throws SQLException {
        // Given
        ViolationStrategy strategy = ViolationStrategy.WARN;
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        try {
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // When - create statement
            Connection conn = wrappedDs.getConnection();
            Statement stmt = conn.createStatement();

            // Then - statement should be proxied
            assertThat(Proxy.isProxyClass(stmt.getClass()))
                .as("Statement should be a JDK dynamic proxy")
                .isTrue();
            assertThat(stmt).isInstanceOf(Statement.class);

        } catch (ClassNotFoundException e) {
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed to invoke methods: " + e.getMessage());
        }
    }

    /**
     * Test 5: Verify PreparedStatement proxy intercepts prepareStatement.
     * <p>Layer 3 - PreparedStatement should be proxied with SQL captured.</p>
     */
    @Test
    @DisplayName("testHikariProxy_preparedStatementProxy_intercepts")
    void testHikariProxy_preparedStatementProxy_intercepts() throws SQLException {
        // Given
        String sql = "SELECT * FROM users WHERE id = ?";
        ViolationStrategy strategy = ViolationStrategy.WARN;
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);

        try {
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // When - prepare statement
            Connection conn = wrappedDs.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);

            // Then - PreparedStatement should be proxied
            assertThat(Proxy.isProxyClass(pstmt.getClass()))
                .as("PreparedStatement should be a JDK dynamic proxy")
                .isTrue();
            assertThat(pstmt).isInstanceOf(PreparedStatement.class);

            // Verify validator was called with the SQL
            verify(validator, atLeastOnce()).validate(any());

        } catch (ClassNotFoundException e) {
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed to invoke methods: " + e.getMessage());
        }
    }

    /**
     * Test 6: Verify CallableStatement proxy intercepts prepareCall.
     * <p>Layer 3 - CallableStatement should be proxied.</p>
     */
    @Test
    @DisplayName("testHikariProxy_callableStatementProxy_intercepts")
    void testHikariProxy_callableStatementProxy_intercepts() throws SQLException {
        // Given
        String sql = "{call myProcedure(?)}";
        ViolationStrategy strategy = ViolationStrategy.WARN;
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareCall(sql)).thenReturn(mockCallableStatement);

        try {
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // When - prepare call
            Connection conn = wrappedDs.getConnection();
            CallableStatement cstmt = conn.prepareCall(sql);

            // Then - CallableStatement should be proxied
            assertThat(Proxy.isProxyClass(cstmt.getClass()))
                .as("CallableStatement should be a JDK dynamic proxy")
                .isTrue();

        } catch (ClassNotFoundException e) {
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed to invoke methods: " + e.getMessage());
        }
    }

    /**
     * Test 7: Verify JDK dynamic proxy pattern is used.
     * <p>All proxies should use java.lang.reflect.Proxy.</p>
     */
    @Test
    @DisplayName("testHikariProxy_jdkDynamicProxy_works")
    void testHikariProxy_jdkDynamicProxy_works() throws SQLException {
        // Given
        ViolationStrategy strategy = ViolationStrategy.WARN;
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        try {
            Class<?> factoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            
            java.lang.reflect.Method wrapMethod = factoryClass.getMethod(
                "wrap", DataSource.class, DefaultSqlSafetyValidator.class, ViolationStrategy.class);
            DataSource wrappedDs = (DataSource) wrapMethod.invoke(null, mockDataSource, validator, strategy);

            // Verify all layers use JDK dynamic proxy
            assertThat(Proxy.isProxyClass(wrappedDs.getClass()))
                .as("DataSource should use JDK dynamic proxy")
                .isTrue();

            Connection conn = wrappedDs.getConnection();
            assertThat(Proxy.isProxyClass(conn.getClass()))
                .as("Connection should use JDK dynamic proxy")
                .isTrue();

            Statement stmt = conn.createStatement();
            assertThat(Proxy.isProxyClass(stmt.getClass()))
                .as("Statement should use JDK dynamic proxy")
                .isTrue();

            // Verify InvocationHandler is retrieved correctly
            InvocationHandler handler = Proxy.getInvocationHandler(wrappedDs);
            assertThat(handler)
                .as("Proxy should have InvocationHandler")
                .isNotNull();

        } catch (ClassNotFoundException e) {
            fail("HikariSqlSafetyProxyFactory should exist");
        } catch (Exception e) {
            fail("Failed: " + e.getMessage());
        }
    }

    /**
     * Test 8: Verify ViolationStrategy from common module is used.
     * <p>Should use unified ViolationStrategy from jdbc-common.</p>
     */
    @Test
    @DisplayName("testHikariProxy_violationStrategy_usesCommon")
    void testHikariProxy_violationStrategy_usesCommon() {
        // Verify ViolationStrategy is from common package
        assertThat(ViolationStrategy.class.getPackage().getName())
            .as("ViolationStrategy should be from jdbc-common package")
            .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");

        // Verify all strategy values are available
        assertThat(ViolationStrategy.values())
            .as("All violation strategies should be available")
            .contains(ViolationStrategy.BLOCK, ViolationStrategy.WARN, ViolationStrategy.LOG);

        // Verify helper methods exist
        assertThat(ViolationStrategy.BLOCK.shouldBlock()).isTrue();
        assertThat(ViolationStrategy.WARN.shouldBlock()).isFalse();
        assertThat(ViolationStrategy.LOG.shouldBlock()).isFalse();

        assertThat(ViolationStrategy.BLOCK.shouldLog()).isTrue();
        assertThat(ViolationStrategy.WARN.shouldLog()).isTrue();
        assertThat(ViolationStrategy.LOG.shouldLog()).isTrue();
    }

    /**
     * Test 9: Verify audit event uses common builder pattern.
     * <p>Audit events should be built using JdbcAuditEventBuilder from common module.</p>
     */
    @Test
    @DisplayName("testHikariProxy_auditEvent_usesCommonBuilder")
    void testHikariProxy_auditEvent_usesCommonBuilder() {
        // Verify JdbcAuditEventBuilder exists in common module
        try {
            Class<?> builderClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.common.JdbcAuditEventBuilder");
            
            assertThat(builderClass)
                .as("JdbcAuditEventBuilder should exist in common module")
                .isNotNull();

        } catch (ClassNotFoundException e) {
            fail("JdbcAuditEventBuilder should exist in jdbc-common module");
        }
    }

    /**
     * Test 10: Verify HikariInterceptorConfig extends JdbcInterceptorConfig.
     * <p>Configuration should extend common config interface.</p>
     */
    @Test
    @DisplayName("testHikariProxy_configuration_extendsCommon")
    void testHikariProxy_configuration_extendsCommon() {
        // Verify HikariInterceptorConfig exists and extends JdbcInterceptorConfig
        try {
            Class<?> hikariConfigClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariInterceptorConfig");
            
            assertThat(JdbcInterceptorConfig.class.isAssignableFrom(hikariConfigClass))
                .as("HikariInterceptorConfig should extend JdbcInterceptorConfig")
                .isTrue();

            // Verify it's an interface
            assertThat(hikariConfigClass.isInterface())
                .as("HikariInterceptorConfig should be an interface")
                .isTrue();

        } catch (ClassNotFoundException e) {
            // Class not yet implemented - test documents expected behavior
            // This is expected in TDD - RED phase
            fail("HikariInterceptorConfig interface should exist and extend JdbcInterceptorConfig");
        }
    }
}

