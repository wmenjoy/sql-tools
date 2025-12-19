package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MpSqlAuditInnerInterceptor.
 * 
 * Tests post-execution interception, IPage extraction,
 * QueryWrapper detection, and audit event generation.
 */
class MpSqlAuditInnerInterceptorTest {

    private AuditLogWriter mockWriter;
    private MpSqlAuditInnerInterceptor interceptor;
    private Executor mockExecutor;
    private ArgumentCaptor<AuditEvent> eventCaptor;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        mockWriter = mock(AuditLogWriter.class);
        interceptor = new MpSqlAuditInnerInterceptor(mockWriter);
        mockExecutor = mock(Executor.class);
        eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        configuration = new Configuration();

        // Clear ThreadLocal before each test
        MpSqlSafetyInnerInterceptor.clearValidationResult();
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal after each test
        MpSqlSafetyInnerInterceptor.clearValidationResult();
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor_nullAuditLogWriter_throwsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MpSqlAuditInnerInterceptor(null)
        );
        assertTrue(exception.getMessage().contains("auditLogWriter"));
    }

    @Test
    void testConstructor_validAuditLogWriter_success() {
        MpSqlAuditInnerInterceptor instance = new MpSqlAuditInnerInterceptor(mockWriter);
        assertNotNull(instance);
    }

    // ==================== Basic Interception Tests ====================

    @Test
    void testUpdateInterception_shouldLogAudit() throws Throwable {
        // Arrange
        String sql = "UPDATE users SET status = 'inactive' WHERE id = ?";
        String mapperId = "UserMapper.updateStatus";
        int rowsAffected = 3;

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
        assertEquals(mapperId, event.getMapperId());
        assertEquals(rowsAffected, event.getRowsAffected());
        assertNull(event.getErrorMessage());
        assertTrue(event.getExecutionTimeMs() >= 0);
    }

    @Test
    void testQueryInterception_shouldLogAudit() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        String mapperId = "UserMapper.selectById";
        List<Object> resultList = Arrays.asList(new Object(), new Object());

        MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, new HashMap<>(), RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(resultList);

        // Act
        Object result = interceptor.intercept(invocation);

        // Assert
        assertEquals(resultList, result);
        verify(mockWriter, times(1)).writeAuditLog(eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertEquals(sql, event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals(mapperId, event.getMapperId());
        assertEquals(2, event.getRowsAffected());
        assertNull(event.getErrorMessage());
    }

    @Test
    void testQueryInterception_emptyResult_shouldLogZeroRows() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE status = 'deleted'";
        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectDeleted",
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
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        assertEquals(0, eventCaptor.getValue().getRowsAffected());
    }

    @Test
    void testInterception_writerThrowsException_shouldNotPropagate() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectAll",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Audit write failed"))
            .when(mockWriter).writeAuditLog(any());

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> interceptor.intercept(invocation));
    }

    // ==================== IPage Extraction Tests ====================

    @Test
    void testIPageExtraction_directIPageParameter_shouldCaptureMetadata() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users LIMIT 20 OFFSET 20";
        Page<User> page = new Page<>(2, 20);
        page.setTotal(100); // Simulate count query result

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectPage",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, page, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Arrays.asList(new Object(), new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(100L, params.get("pagination.total"));
        assertEquals(2L, params.get("pagination.current"));
        assertEquals(20L, params.get("pagination.size"));
        assertEquals(5L, params.get("pagination.pages")); // 100 / 20 = 5
    }

    @Test
    void testIPageExtraction_iPageInMapParameter_shouldCaptureMetadata() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE status = ? LIMIT 10";
        Page<User> page = new Page<>(1, 10);
        page.setTotal(25);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("status", "active");
        paramMap.put("page", page);

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectByStatus",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, paramMap, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(25L, params.get("pagination.total"));
        assertEquals(1L, params.get("pagination.current"));
        assertEquals(10L, params.get("pagination.size"));
    }

    @Test
    void testIPageExtraction_noIPage_shouldNotAddPaginationMetadata() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectById",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, 123, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        // params should be null or not contain pagination keys
        if (params != null) {
            assertFalse(params.containsKey("pagination.total"));
        }
    }

    // ==================== QueryWrapper Detection Tests ====================

    @Test
    void testQueryWrapperCorrelation_queryWrapper_shouldSetFlag() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE status = 'active'";
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active");

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectList",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, wrapper, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(true, params.get("queryWrapper"));
    }

    @Test
    void testQueryWrapperCorrelation_lambdaQueryWrapper_shouldDetect() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE status = 'active'";
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, "active");

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectList",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, wrapper, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(true, params.get("queryWrapper"));
    }

    @Test
    void testQueryWrapperCorrelation_updateWrapper_shouldCaptureResults() throws Throwable {
        // Arrange
        String sql = "UPDATE users SET status = 'inactive' WHERE id = 1";
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.set("status", "inactive").eq("id", 1);
        int rowsAffected = 1;

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.update",
            org.apache.ibatis.mapping.SqlCommandType.UPDATE);

        Method updateMethod = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        Object[] args = {mappedStatement, wrapper};
        Invocation invocation = new Invocation(mockExecutor, updateMethod, args);

        when(mockExecutor.update(any(MappedStatement.class), any())).thenReturn(rowsAffected);

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(true, params.get("queryWrapper"));
        assertEquals(1, event.getRowsAffected());
    }

    @Test
    void testQueryWrapperCorrelation_wrapperInMap_shouldDetect() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE name LIKE ?";
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.like("name", "Alice");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ew", wrapper);
        paramMap.put("tenant", "default");

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectByName",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, paramMap, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        assertEquals(true, params.get("queryWrapper"));
    }

    @Test
    void testQueryWrapperCorrelation_noWrapper_shouldNotSetFlag() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectById",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, 1, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        // params should be null or not contain queryWrapper key
        if (params != null) {
            assertFalse(params.containsKey("queryWrapper"));
        }
    }

    // ==================== IPage + QueryWrapper Combined Tests ====================

    @Test
    void testCombinedIPageAndQueryWrapper_shouldCaptureBoth() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 10";
        Page<User> page = new Page<>(1, 10);
        page.setTotal(50);

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("page", page);
        paramMap.put("ew", wrapper);

        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectPageWithWrapper",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, paramMap, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Arrays.asList(new Object(), new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        Map<String, Object> params = event.getParams();
        
        assertNotNull(params);
        
        // Verify pagination metadata
        assertEquals(50L, params.get("pagination.total"));
        assertEquals(1L, params.get("pagination.current"));
        assertEquals(10L, params.get("pagination.size"));
        
        // Verify QueryWrapper flag
        assertEquals(true, params.get("queryWrapper"));
    }

    // ==================== Timing Tests ====================

    @Test
    void testExecutionTiming_shouldCaptureAccurately() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        MappedStatement mappedStatement = createMappedStatement(sql, "UserMapper.selectAll",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenAnswer(inv -> {
                Thread.sleep(10);
                return Collections.singletonList(new Object());
            });

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertTrue(event.getExecutionTimeMs() >= 10,
                   "Execution time should be at least 10ms, got: " + event.getExecutionTimeMs());
    }

    // ==================== Validation Correlation Tests ====================

    @Test
    void testValidationCorrelation_shouldIncludeViolations() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        ValidationResult validationResult = ValidationResult.pass();
        validationResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");

        // Simulate safety interceptor setting validation result
        setValidationResult(validationResult);

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
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        assertNotNull(event.getViolations());
        assertFalse(event.getViolations().isPassed());
        assertEquals(RiskLevel.HIGH, event.getViolations().getRiskLevel());
        assertEquals(1, event.getViolations().getViolations().size());
    }

    @Test
    void testValidationCorrelation_noViolations_shouldNotInclude() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        ValidationResult validationResult = ValidationResult.pass();

        setValidationResult(validationResult);

        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenReturn(Collections.singletonList(new Object()));

        // Act
        interceptor.intercept(invocation);

        // Assert
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        
        // Should not include violations for passed validation
        assertNull(event.getViolations());
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testExceptionDuringExecution_shouldLogError() throws Throwable {
        // Arrange
        String sql = "SELECT * FROM users";
        MappedStatement mappedStatement = createMappedStatement(sql, "test",
            org.apache.ibatis.mapping.SqlCommandType.SELECT);

        Method queryMethod = Executor.class.getMethod("query", 
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        Object[] args = {mappedStatement, null, RowBounds.DEFAULT, null};
        Invocation invocation = new Invocation(mockExecutor, queryMethod, args);

        RuntimeException expectedException = new RuntimeException("Connection timeout");
        when(mockExecutor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
            .thenThrow(expectedException);

        // Act & Assert - Invocation wraps exceptions in InvocationTargetException
        assertThrows(Throwable.class, () -> interceptor.intercept(invocation));

        // Verify audit event was still written with error
        verify(mockWriter).writeAuditLog(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertEquals("Connection timeout", event.getErrorMessage());
        assertEquals(-1, event.getRowsAffected()); // No result on error
    }

    // ==================== Helper Methods ====================

    private MappedStatement createMappedStatement(String sql, String id, 
                                                   org.apache.ibatis.mapping.SqlCommandType commandType) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
            configuration, id, createSqlSource(sql), commandType);
        return builder.build();
    }

    private SqlSource createSqlSource(final String sql) {
        return new SqlSource() {
            @Override
            public BoundSql getBoundSql(Object parameterObject) {
                return new BoundSql(configuration, sql, Collections.emptyList(), parameterObject);
            }
        };
    }

    /**
     * Simulates MpSqlSafetyInnerInterceptor setting validation result.
     * Uses reflection to access the private ThreadLocal.
     */
    private void setValidationResult(ValidationResult result) {
        try {
            java.lang.reflect.Field field = MpSqlSafetyInnerInterceptor.class
                .getDeclaredField("VALIDATION_RESULT");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<ValidationResult> threadLocal = (ThreadLocal<ValidationResult>) field.get(null);
            threadLocal.set(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set validation result", e);
        }
    }
}






