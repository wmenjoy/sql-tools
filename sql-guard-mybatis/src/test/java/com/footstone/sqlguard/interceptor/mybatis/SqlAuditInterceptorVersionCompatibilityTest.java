package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for SqlAuditInterceptor MyBatis version compatibility.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>MyBatis 3.4.x compatibility</li>
 *   <li>MyBatis 3.5.x compatibility</li>
 *   <li>BoundSql extraction across versions</li>
 *   <li>Result extraction across versions</li>
 *   <li>Dynamic SQL resolution</li>
 * </ul>
 *
 * <p>Note: These tests verify the interceptor works with both MyBatis versions.
 * The actual version testing is done via Maven profiles (mybatis-3.4 and mybatis-3.5).</p>
 */
class SqlAuditInterceptorVersionCompatibilityTest {

    private AuditLogWriter mockWriter;
    private SqlAuditInterceptor interceptor;
    private Executor mockExecutor;
    private ArgumentCaptor<AuditEvent> eventCaptor;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        mockWriter = mock(AuditLogWriter.class);
        interceptor = new SqlAuditInterceptor(mockWriter);
        mockExecutor = mock(Executor.class);
        eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        configuration = new Configuration();
    }

    @Test
    void testMyBatis34_interceptor_shouldWork() throws Throwable {
        // This test verifies the interceptor works with MyBatis 3.4.x API
        // When run with -Pmybatis-3.4 profile, it uses MyBatis 3.4.6

        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        String mapperId = "com.example.UserMapper.selectById";

        MappedStatement ms = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query",
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {ms, 123L, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        Object result = interceptor.intercept(invocation);

        // Assert
        assertNotNull(result);
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals(mapperId, event.getMapperId());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testMyBatis34_boundSql_shouldExtract() throws Throwable {
        // Verify BoundSql extraction works in MyBatis 3.4.x

        // Arrange
        String expectedSql = "SELECT * FROM users WHERE status = 'ACTIVE'";
        String mapperId = "com.example.UserMapper.selectActive";

        MappedStatement ms = createMappedStatement(expectedSql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query",
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {ms, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert - verify SQL was extracted from BoundSql
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        assertEquals(expectedSql, eventCaptor.getValue().getSql());
    }

    @Test
    void testMyBatis34_resultExtraction_shouldWork() throws Throwable {
        // Verify result extraction works in MyBatis 3.4.x

        // Arrange
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        String mapperId = "com.example.UserMapper.updateName";

        MappedStatement ms = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.UPDATE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {ms, 456L};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(3);

        // Act
        interceptor.intercept(invocation);

        // Assert - verify rows affected was extracted
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        assertEquals(3, eventCaptor.getValue().getRowsAffected());
    }

    @Test
    void testMyBatis35_interceptor_shouldWork() throws Throwable {
        // This test verifies the interceptor works with MyBatis 3.5.x API
        // When run with -Pmybatis-3.5 profile (default), it uses MyBatis 3.5.13

        // Arrange
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        String mapperId = "com.example.UserMapper.insert";

        MappedStatement ms = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.INSERT);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {ms, new Object()};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(1);

        // Act
        Object result = interceptor.intercept(invocation);

        // Assert
        assertNotNull(result);
        assertEquals(1, result);
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        
        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.INSERT, event.getSqlType());
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testMyBatis35_boundSql_shouldExtract() throws Throwable {
        // Verify BoundSql extraction works in MyBatis 3.5.x (enhanced API)

        // Arrange
        String expectedSql = "DELETE FROM sessions WHERE expired_at < NOW()";
        String mapperId = "com.example.SessionMapper.deleteExpired";

        MappedStatement ms = createMappedStatement(expectedSql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.DELETE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {ms, null};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(10);

        // Act
        interceptor.intercept(invocation);

        // Assert - verify SQL was extracted from BoundSql
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        assertEquals(expectedSql, eventCaptor.getValue().getSql());
    }

    @Test
    void testMyBatis35_resultExtraction_shouldWork() throws Throwable {
        // Verify result extraction works in MyBatis 3.5.x

        // Arrange
        String sql = "SELECT * FROM products WHERE category = ?";
        String mapperId = "com.example.ProductMapper.selectByCategory";

        MappedStatement ms = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query",
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {ms, "Electronics", RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(java.util.Arrays.asList(new Object(), new Object(), new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert - verify result size was captured
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        assertEquals(3, eventCaptor.getValue().getRowsAffected());
    }

    @Test
    void testVersionDetection_shouldIdentifyCorrectly() throws Exception {
        // This test verifies we can detect MyBatis version if needed
        // Currently, our implementation doesn't need version-specific code,
        // but this test documents version compatibility

        // Arrange & Act
        String mybatisVersion = org.apache.ibatis.session.Configuration.class.getPackage()
            .getImplementationVersion();

        // Assert - version should be either 3.4.x or 3.5.x depending on profile
        // Note: Implementation version might be null in some environments
        if (mybatisVersion != null) {
            assertTrue(mybatisVersion.startsWith("3.4") || mybatisVersion.startsWith("3.5"),
                "MyBatis version should be 3.4.x or 3.5.x, got: " + mybatisVersion);
        } else {
            // If version is null, verify Configuration class exists (basic compatibility check)
            assertNotNull(configuration);
        }
        
        // Verify writer was not called (this test doesn't use interceptor)
        verify(mockWriter, never()).writeAuditLog(any());
    }

    @Test
    void testDynamicSql_bothVersions_shouldResolve() throws Throwable {
        // Verify dynamic SQL resolution works in both MyBatis versions
        // This simulates <if>, <where>, <foreach> tags being resolved

        // Arrange - simulate resolved dynamic SQL
        String resolvedSql = "SELECT * FROM users WHERE name = ? AND age > ?";
        String mapperId = "com.example.UserMapper.selectByNameAndAge";

        MappedStatement ms = createMappedStatement(resolvedSql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query",
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {ms, new Object(), RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert - verify resolved SQL was captured
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        String capturedSql = eventCaptor.getValue().getSql();
        assertEquals(resolvedSql, capturedSql);

        // Verify SQL doesn't contain MyBatis dynamic tags
        assertFalse(capturedSql.contains("<if"));
        assertFalse(capturedSql.contains("<where"));
        assertFalse(capturedSql.contains("<foreach"));
    }

    @Test
    void testTimingAccuracy_bothVersions() throws Throwable {
        // Verify timing measurement works accurately in both versions

        // Arrange
        String sql = "SELECT * FROM users";
        MappedStatement ms = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query",
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {ms, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenAnswer(inv -> {
                Thread.sleep(30);
                return Collections.emptyList();
            });

        // Act
        interceptor.intercept(invocation);

        // Assert - verify timing was measured
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        long executionTime = eventCaptor.getValue().getExecutionTimeMs();
        assertTrue(executionTime >= 25 && executionTime <= 50,
            "Expected ~30ms, got " + executionTime + "ms");
    }

    /**
     * Helper method to create MappedStatement.
     * This works with both MyBatis 3.4.x and 3.5.x.
     */
    private MappedStatement createMappedStatement(String sql, String mapperId,
                                                   org.apache.ibatis.mapping.SqlCommandType commandType) {
        org.apache.ibatis.mapping.SqlSource sqlSource = new org.apache.ibatis.mapping.SqlSource() {
            @Override
            public BoundSql getBoundSql(Object parameterObject) {
                return new BoundSql(configuration, sql, new ArrayList<>(), parameterObject);
            }
        };

        MappedStatement.Builder builder = new MappedStatement.Builder(
            configuration,
            mapperId,
            sqlSource,
            commandType
        );

        return builder.build();
    }
}





