package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqlAuditInterceptor.
 */
class SqlAuditInterceptorTest {

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

        // Clear ThreadLocal before each test
        SqlInterceptorContext.VALIDATION_RESULT.remove();
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal after each test
        SqlInterceptorContext.VALIDATION_RESULT.remove();
    }

    @Test
    void testConstructor_nullWriter_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> new SqlAuditInterceptor(null));
    }

    @Test
    void testUpdateInterception_shouldLogAudit() throws Throwable {
        // Arrange
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        String mapperId = "UserMapper.updateUser";
        int rowsAffected = 1;

        MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.UPDATE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, new HashMap<>()};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(rowsAffected);

        // Act
        Object result = interceptor.intercept(invocation);

        // Assert
        assertEquals(rowsAffected, result);
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.UPDATE, event.getSqlType());
        assertEquals(mapperId, event.getStatementId());
        assertEquals(rowsAffected, event.getRowsAffected());
        assertNull(event.getErrorMessage());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testQueryInterception_shouldLogAudit() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        String mapperId = "UserMapper.selectById";
        List<Object> resultList = Arrays.asList(new Object(), new Object(), new Object());

        MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, new HashMap<>(), RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any())).thenReturn(resultList);

        // Act
        Object result = interceptor.intercept(invocation);

        // Assert
        assertEquals(resultList, result);
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals(mapperId, event.getStatementId());
        assertEquals(3, event.getRowsAffected());
        assertNull(event.getErrorMessage());
    }

    @Test
    void testTimingMeasurement_shouldBeAccurate() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenAnswer(inv -> {
                Thread.sleep(50);
                return Collections.emptyList();
            });

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertTrue(event.getExecutionTimeMs() >= 45 && event.getExecutionTimeMs() <= 70,
            "Expected ~50ms, got " + event.getExecutionTimeMs() + "ms");
    }

    @Test
    void testValidationCorrelation_shouldIncludeViolations() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");

        SqlInterceptorContext.VALIDATION_RESULT.set(validationResult);

        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNotNull(event.getViolations());
        assertFalse(event.getViolations().isPassed());
        assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
        assertEquals(1, event.getViolations().getViolations().size());
    }

    @Test
    void testInterceptorChain_shouldWorkWithSafetyInterceptor() throws Throwable {
        // Arrange
        String sql = "UPDATE users SET name = ?";
        ValidationResult validationResult = ValidationResult.pass();

        SqlInterceptorContext.VALIDATION_RESULT.set(validationResult);

        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.UPDATE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, null};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(1);

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        // Passed validation should not be included in audit event (only failures)
        assertNull(event.getViolations());
    }

    @Test
    void testFailedExecution_shouldCaptureError() throws Throwable {
        // Arrange
        String sql = "UPDATE users SET name = ?";
        SQLException sqlException = new SQLException("Constraint violation");

        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.UPDATE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, null};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenThrow(sqlException);

        // Act & Assert - Invocation.proceed() wraps exceptions in InvocationTargetException
        try {
            interceptor.intercept(invocation);
            fail("Expected exception to be thrown");
        } catch (Throwable t) {
            // Exception should be re-thrown
            assertTrue(t instanceof java.lang.reflect.InvocationTargetException || t instanceof SQLException);
        }

        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertEquals("Constraint violation", event.getErrorMessage());
        assertEquals(-1, event.getRowsAffected());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testParameterExtraction_mapParameter() throws Throwable {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        params.put("name", "John");

        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, params, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNotNull(event.getParams());
        assertEquals(123, event.getParams().get("id"));
        assertEquals("John", event.getParams().get("name"));
    }

    @Test
    void testParameterExtraction_singleParameter() throws Throwable {
        // Arrange
        Integer singleParam = 456;

        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, singleParam, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNotNull(event.getParams());
        assertEquals(456, event.getParams().get("param"));
    }

    @Test
    void testParameterExtraction_nullParameter() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNull(event.getParams());
    }

    @Test
    void testRowsAffected_insertOperation() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("INSERT INTO users VALUES (?)", "test",
            org.apache.ibatis.mapping.SqlCommandType.INSERT);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, null};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(5);

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertEquals(5, event.getRowsAffected());
    }

    @Test
    void testRowsAffected_deleteOperation() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("DELETE FROM users WHERE id = ?", "test",
            org.apache.ibatis.mapping.SqlCommandType.DELETE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, null};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(2);

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertEquals(2, event.getRowsAffected());
    }

    @Test
    void testRowsAffected_emptyResultSet() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users WHERE id = -1", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertEquals(0, event.getRowsAffected());
    }

    @Test
    void testAuditWriterException_shouldNotBreakExecution() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("Audit writer failed")).when(mockWriter).writeAuditLog(any());

        // Act
        assertDoesNotThrow(() -> interceptor.intercept(invocation));

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(any());
    }

    @Test
    void testPlugin_shouldWrapExecutor() {
        // Arrange
        Executor executor = mock(Executor.class);

        // Act
        Object wrapped = interceptor.plugin(executor);

        // Assert
        assertNotNull(wrapped);
        assertNotSame(executor, wrapped);
    }

    @Test
    void testPlugin_nonExecutor_shouldReturnOriginal() {
        // Arrange
        Object nonExecutor = new Object();

        // Act
        Object result = interceptor.plugin(nonExecutor);

        // Assert
        assertSame(nonExecutor, result);
    }

    @Test
    void testSetProperties_shouldNotThrow() {
        // Arrange
        Properties props = new Properties();
        props.setProperty("someProperty", "someValue");

        // Act & Assert
        assertDoesNotThrow(() -> interceptor.setProperties(props));
    }

    @Test
    void testMissingValidationResult_shouldStillLogAudit() throws Throwable {
        // Arrange
        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNull(event.getViolations());
    }

    @Test
    void testPassedValidation_shouldNotIncludeViolations() throws Throwable {
        // Arrange
        ValidationResult validationResult = ValidationResult.pass();

        SqlInterceptorContext.VALIDATION_RESULT.set(validationResult);

        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users WHERE id = ?", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNull(event.getViolations());
    }

    @Test
    void testExceptionWithCause_shouldCaptureCauseMessage() throws Throwable {
        // Arrange
        SQLException cause = new SQLException("Connection timeout");
        RuntimeException wrapper = new RuntimeException("Wrapper exception", cause);

        MappedStatement mappedStatement = createMappedStatement("SELECT * FROM users", "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenThrow(wrapper);

        // Act & Assert - Invocation.proceed() wraps exceptions
        try {
            interceptor.intercept(invocation);
            fail("Expected exception to be thrown");
        } catch (Throwable t) {
            // Exception should be re-thrown (may be wrapped)
            assertTrue(t instanceof RuntimeException || t instanceof java.lang.reflect.InvocationTargetException);
        }

        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        // Should capture cause message if available
        String errorMsg = event.getErrorMessage();
        assertTrue(errorMsg.equals("Connection timeout") || errorMsg.equals("Wrapper exception"),
            "Expected either cause or wrapper message, got: " + errorMsg);
    }

    /**
     * Helper method to create MappedStatement.
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
