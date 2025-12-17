package com.footstone.sqlguard.interceptor.mybatis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.sql.SQLException;
import java.util.ArrayList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for violation handling strategies.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>BLOCK strategy behavior</li>
 *   <li>WARN strategy behavior</li>
 *   <li>LOG strategy behavior</li>
 *   <li>Violation message formatting</li>
 *   <li>Multiple violations handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ViolationHandlingTest {

  @Mock
  private SqlSafetyValidator validator;

  @Mock
  private Executor executor;

  private Configuration configuration;

  @BeforeEach
  void setUp() {
    configuration = new Configuration();
  }

  @Test
  void testBLOCKStrategy_withViolation_shouldThrowSQLException() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "DELETE FROM users";
    String mapperId = "com.example.UserMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    assertNotNull(exception);
    assertTrue(exception.getMessage().contains("SQL Safety Violation (BLOCK)"));
  }

  @Test
  void testBLOCKStrategy_sqlState_shouldBe42000() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "UPDATE users SET status = 'DELETED'";
    String mapperId = "com.example.UserMapper.updateAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    assertEquals("42000", exception.getSQLState());
  }

  @Test
  void testBLOCKStrategy_message_shouldContainDetails() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "DELETE FROM orders";
    String mapperId = "com.example.OrderMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    String message = exception.getMessage();
    assertTrue(message.contains("MapperId: " + mapperId));
    assertTrue(message.contains("Risk Level: HIGH"));
    assertTrue(message.contains("Missing WHERE clause"));
    assertTrue(message.contains("Suggestion: Add WHERE condition"));
  }

  @Test
  void testWARNStrategy_withViolation_shouldLogError() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);

    String sql = "DELETE FROM users";
    String mapperId = "com.example.UserMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act - should not throw exception
    assertDoesNotThrow(() -> interceptor.intercept(invocation));

    // Assert - execution should proceed
    verify(executor, times(1)).update(any(MappedStatement.class), any());
  }

  @Test
  void testWARNStrategy_shouldNotThrow() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);

    String sql = "UPDATE products SET price = 0";
    String mapperId = "com.example.ProductMapper.resetPrices";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {ms, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(100);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert - should not throw
    Object returnValue = assertDoesNotThrow(() -> interceptor.intercept(invocation));
    assertEquals(100, returnValue);
  }

  @Test
  void testWARNStrategy_shouldProceed() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);

    String sql = "DELETE FROM temp_data";
    String mapperId = "com.example.TempMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(50);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.MEDIUM, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act
    Object returnValue = interceptor.intercept(invocation);

    // Assert - execution proceeded and returned result
    assertEquals(50, returnValue);
    verify(executor, times(1)).update(any(MappedStatement.class), any());
  }

  @Test
  void testLOGStrategy_withViolation_shouldLogWarn() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.LOG);

    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, null, org.apache.ibatis.session.RowBounds.DEFAULT, null};
    when(executor.query(any(MappedStatement.class), any(), any(), any()))
        .thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            org.apache.ibatis.session.RowBounds.class, 
            org.apache.ibatis.session.ResultHandler.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.LOW, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act - should not throw exception
    assertDoesNotThrow(() -> interceptor.intercept(invocation));

    // Assert - execution should proceed
    verify(executor, times(1)).query(any(MappedStatement.class), any(), any(), any());
  }

  @Test
  void testLOGStrategy_shouldNotThrow() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.LOG);

    String sql = "UPDATE settings SET value = 'new'";
    String mapperId = "com.example.SettingsMapper.updateAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {ms, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.MEDIUM, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert - should not throw
    Object returnValue = assertDoesNotThrow(() -> interceptor.intercept(invocation));
    assertEquals(1, returnValue);
  }

  @Test
  void testFormatViolations_shouldIncludeMapperId() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "DELETE FROM users";
    String expectedMapperId = "com.example.mapper.UserMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, expectedMapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    assertTrue(exception.getMessage().contains("MapperId: " + expectedMapperId));
  }

  @Test
  void testFormatViolations_shouldIncludeRiskLevel() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "UPDATE users SET deleted = true";
    String mapperId = "com.example.UserMapper.markAllDeleted";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    assertTrue(exception.getMessage().contains("Risk Level: CRITICAL"));
    assertTrue(exception.getMessage().contains("[CRITICAL]"));
  }

  @Test
  void testFormatViolations_shouldIncludeSuggestions() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "DELETE FROM orders";
    String mapperId = "com.example.OrderMapper.deleteAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", 
        "Add WHERE condition to limit scope");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    assertTrue(exception.getMessage().contains("Suggestion: Add WHERE condition to limit scope"));
  }

  @Test
  void testMultipleViolations_shouldFormatAll() throws Throwable {
    // Arrange
    SqlSafetyInterceptor interceptor = 
        new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);

    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, null, org.apache.ibatis.session.RowBounds.DEFAULT, null};
    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            org.apache.ibatis.session.RowBounds.class, 
            org.apache.ibatis.session.ResultHandler.class), args);

    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    result.addViolation(RiskLevel.MEDIUM, "SELECT * usage", "Specify columns explicitly");
    result.addViolation(RiskLevel.LOW, "No LIMIT clause", "Add LIMIT to prevent large result sets");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class,
        () -> interceptor.intercept(invocation));

    String message = exception.getMessage();
    // Should contain all three violations
    assertTrue(message.contains("Missing WHERE clause"));
    assertTrue(message.contains("SELECT * usage"));
    assertTrue(message.contains("No LIMIT clause"));
    // Should contain all suggestions
    assertTrue(message.contains("Add WHERE condition"));
    assertTrue(message.contains("Specify columns explicitly"));
    assertTrue(message.contains("Add LIMIT to prevent large result sets"));
    // Should show highest risk level
    assertTrue(message.contains("Risk Level: HIGH"));
  }

  /**
   * Helper method to create MappedStatement.
   */
  private MappedStatement createMappedStatement(String sql, String mapperId,
                                                 org.apache.ibatis.mapping.SqlCommandType commandType) {
    SqlSource sqlSource = new SqlSource() {
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

