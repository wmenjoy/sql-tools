package com.footstone.sqlguard.interceptor.mybatis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SqlSafetyInterceptor.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Query and update interception</li>
 *   <li>RowBounds detection</li>
 *   <li>BoundSql extraction</li>
 *   <li>MapperId extraction</li>
 *   <li>Violation strategy handling (BLOCK/WARN/LOG)</li>
 *   <li>Plugin wrapping</li>
 *   <li>Properties configuration</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SqlSafetyInterceptorTest {

  @Mock
  private SqlSafetyValidator validator;

  @Mock
  private Executor executor;

  @Mock
  private ResultHandler resultHandler;

  private SqlSafetyInterceptor interceptor;
  private Configuration configuration;

  @BeforeEach
  void setUp() {
    interceptor = new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);
    configuration = new Configuration();
  }

  @Test
  void testConstructor_withNullValidator_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new SqlSafetyInterceptor(null, ViolationStrategy.WARN)
    );
    assertEquals("validator cannot be null", exception.getMessage());
  }

  @Test
  void testConstructor_withNullStrategy_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new SqlSafetyInterceptor(validator, null)
    );
    assertEquals("strategy cannot be null", exception.getMessage());
  }

  @Test
  void testQueryInterception_shouldValidate() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    String mapperId = "com.example.UserMapper.selectById";
    Object parameter = 1L;
    RowBounds rowBounds = new RowBounds(0, 10);

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, rowBounds, resultHandler};
    
    // Mock executor to return a result
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), 
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator, times(1)).validate(any(SqlContext.class));
  }

  @Test
  void testUpdateInterception_shouldValidate() throws Throwable {
    // Arrange
    String sql = "UPDATE users SET name = ? WHERE id = ?";
    String mapperId = "com.example.UserMapper.updateById";
    Object parameter = new Object();

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {mappedStatement, parameter};
    
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator, times(1)).validate(any(SqlContext.class));
  }

  @Test
  void testInsertInterception_shouldValidate() throws Throwable {
    // Arrange
    String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
    String mapperId = "com.example.UserMapper.insert";
    Object parameter = new Object();

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.INSERT);

    Object[] args = {mappedStatement, parameter};
    
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator, times(1)).validate(any(SqlContext.class));
  }

  @Test
  void testRowBoundsDetection_shouldExtract() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";
    Object parameter = null;
    RowBounds rowBounds = new RowBounds(10, 20);

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, rowBounds, resultHandler};
    
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), 
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator).validate(argThat(context ->
        context.getRowBounds() != null &&
        context.getRowBounds() == rowBounds
    ));
  }

  @Test
  void testBoundSqlExtraction_shouldGetResolvedSql() throws Throwable {
    // Arrange
    String resolvedSql = "SELECT * FROM users WHERE status = 'ACTIVE'";
    String mapperId = "com.example.UserMapper.selectActive";
    Object parameter = null;

    MappedStatement mappedStatement = createMappedStatement(resolvedSql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, RowBounds.DEFAULT, resultHandler};
    
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), 
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator).validate(argThat(context ->
        context.getSql().equals(resolvedSql)
    ));
  }

  @Test
  void testMapperIdExtraction_shouldGetMapperId() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";
    Object parameter = null;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, RowBounds.DEFAULT, resultHandler};
    
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), 
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    interceptor.intercept(invocation);

    // Assert
    verify(validator).validate(argThat(context ->
        context.getStatementId().equals(mapperId)
    ));
  }

  @Test
  void testBLOCKStrategy_shouldThrowException() throws Throwable {
    // Arrange
    SqlSafetyInterceptor blockInterceptor =
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "DELETE FROM users";
    String mapperId = "com.example.UserMapper.deleteAll";
    Object parameter = null;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {mappedStatement, parameter};
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult failedResult = ValidationResult.pass();
    failedResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause",
        "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(failedResult);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> blockInterceptor.intercept(invocation));

    assertTrue(exception.getMessage().contains("SQL Safety Violation (BLOCK)"));
    assertEquals("42000", exception.getSQLState());
    verify(executor, never()).update(any(MappedStatement.class), any());
  }

  @Test
  void testWARNStrategy_shouldLogAndContinue() throws Throwable {
    // Arrange
    SqlSafetyInterceptor warnInterceptor =
        new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);

    String sql = "DELETE FROM users";
    String mapperId = "com.example.UserMapper.deleteAll";
    Object parameter = null;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {mappedStatement, parameter};
    
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult failedResult = ValidationResult.pass();
    failedResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause",
        "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(failedResult);

    // Act
    Object result = warnInterceptor.intercept(invocation);

    // Assert
    assertNotNull(result);
    verify(executor, times(1)).update(any(MappedStatement.class), any());
  }

  @Test
  void testLOGStrategy_shouldOnlyLog() throws Throwable {
    // Arrange
    SqlSafetyInterceptor logInterceptor =
        new SqlSafetyInterceptor(validator, ViolationStrategy.LOG);

    String sql = "DELETE FROM users";
    String mapperId = "com.example.UserMapper.deleteAll";
    Object parameter = null;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {mappedStatement, parameter};
    
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult failedResult = ValidationResult.pass();
    failedResult.addViolation(RiskLevel.MEDIUM, "Missing WHERE clause",
        "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(failedResult);

    // Act
    Object result = logInterceptor.intercept(invocation);

    // Assert
    assertNotNull(result);
    verify(executor, times(1)).update(any(MappedStatement.class), any());
  }

  @Test
  void testValidSql_shouldProceed() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    String mapperId = "com.example.UserMapper.selectById";
    Object parameter = 1L;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, RowBounds.DEFAULT, resultHandler};
    
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), 
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    ValidationResult validResult = ValidationResult.pass();
    when(validator.validate(any(SqlContext.class))).thenReturn(validResult);

    // Act
    Object result = interceptor.intercept(invocation);

    // Assert
    assertNotNull(result);
    verify(executor, times(1)).query(any(MappedStatement.class), any(), 
        any(RowBounds.class), any(ResultHandler.class));
  }

  @Test
  void testPluginWrap_shouldWrapExecutor() {
    // Act
    Object wrapped = interceptor.plugin(executor);

    // Assert
    assertNotNull(wrapped);
    // Plugin.wrap returns a proxy or the original object
    assertTrue(wrapped != null);
  }

  @Test
  void testSetProperties_shouldLoadConfig() {
    // Arrange
    Properties properties = new Properties();
    properties.setProperty("strategy", "BLOCK");

    // Act & Assert - should not throw exception
    assertDoesNotThrow(() -> interceptor.setProperties(properties));
  }

  /**
   * Helper method to create MappedStatement.
   */
  private MappedStatement createMappedStatement(String sql, String mapperId,
                                                 org.apache.ibatis.mapping.SqlCommandType commandType) {
    // Create SqlSource that returns our BoundSql
    SqlSource sqlSource = new SqlSource() {
      @Override
      public BoundSql getBoundSql(Object parameterObject) {
        return new BoundSql(configuration, sql, new ArrayList<>(), parameterObject);
      }
    };

    // Build MappedStatement
    MappedStatement.Builder builder = new MappedStatement.Builder(
        configuration,
        mapperId,
        sqlSource,
        commandType
    );

    return builder.build();
  }
}
