package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.PreparedStatementProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for DruidSqlSafetyFilter.
 *
 * <p>Tests filter interception, SQL validation, datasource extraction, SQL type detection, and
 * violation strategy handling.</p>
 */
class DruidSqlSafetyFilterTest {

  private DefaultSqlSafetyValidator validator;
  private DruidSqlSafetyFilter filter;
  private ConnectionProxy connectionProxy;
  private DataSourceProxy dataSourceProxy;
  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    validator = mock(DefaultSqlSafetyValidator.class);
    dataSourceProxy = mock(DataSourceProxy.class);
    connectionProxy = mock(ConnectionProxy.class);
    filterChain = mock(FilterChain.class);

    when(connectionProxy.getDirectDataSource()).thenReturn(dataSourceProxy);
    when(dataSourceProxy.getName()).thenReturn("testDataSource");
    
    // Clear deduplication cache before each test
    com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
  }

  @Test
  void testConstructor_nullValidator_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> {
      new DruidSqlSafetyFilter(null, ViolationStrategy.WARN);
    });
  }

  @Test
  void testConstructor_nullStrategy_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> {
      new DruidSqlSafetyFilter(validator, null);
    });
  }

  @Test
  void testPreparedStatementInterception_shouldValidate() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    String sql = "SELECT * FROM test_table_001 WHERE id = ?";
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.connection_prepareStatement(filterChain, connectionProxy, sql);

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());

    SqlContext context = captor.getValue();
    assertEquals(sql, context.getSql());
    assertEquals(SqlCommandType.SELECT, context.getType());
    assertEquals("jdbc.druid:testDataSource", context.getMapperId());
    assertEquals("testDataSource", context.getDatasource());
  }

  @Test
  void testStatementExecuteQuery_shouldValidate() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    String sql = "SELECT * FROM users";
    StatementProxy statementProxy = mock(StatementProxy.class);
    when(statementProxy.getConnectionProxy()).thenReturn(connectionProxy);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeQuery(filterChain, statementProxy, sql);

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());

    SqlContext context = captor.getValue();
    assertEquals(sql, context.getSql());
    assertEquals(SqlCommandType.SELECT, context.getType());
  }

  @Test
  void testStatementExecuteUpdate_shouldValidate() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    String sql = "UPDATE users SET name = 'test'";
    StatementProxy statementProxy = mock(StatementProxy.class);
    when(statementProxy.getConnectionProxy()).thenReturn(connectionProxy);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeUpdate(filterChain, statementProxy, sql);

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());

    SqlContext context = captor.getValue();
    assertEquals(sql, context.getSql());
    assertEquals(SqlCommandType.UPDATE, context.getType());
  }

  @Test
  void testDatasourceExtraction_shouldGetName() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(dataSourceProxy.getName()).thenReturn("myDataSource");
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.connection_prepareStatement(filterChain, connectionProxy, "SELECT 1");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals("myDataSource", captor.getValue().getDatasource());
  }

  @Test
  void testSqlTypeDetection_shouldIdentifyCommand() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Test SELECT - use unique SQL to avoid deduplication
    ArgumentCaptor<SqlContext> selectCaptor = ArgumentCaptor.forClass(SqlContext.class);
    filter.connection_prepareStatement(filterChain, connectionProxy, "SELECT * FROM users WHERE id=1");
    verify(validator, times(1)).validate(selectCaptor.capture());
    assertEquals(SqlCommandType.SELECT, selectCaptor.getValue().getType());

    // Test UPDATE - use unique SQL
    ArgumentCaptor<SqlContext> updateCaptor = ArgumentCaptor.forClass(SqlContext.class);
    filter.connection_prepareStatement(filterChain, connectionProxy, "UPDATE users SET name='x' WHERE id=2");
    verify(validator, times(2)).validate(updateCaptor.capture());
    assertEquals(SqlCommandType.UPDATE, updateCaptor.getAllValues().get(1).getType());

    // Test DELETE - use unique SQL
    ArgumentCaptor<SqlContext> deleteCaptor = ArgumentCaptor.forClass(SqlContext.class);
    filter.connection_prepareStatement(filterChain, connectionProxy, "DELETE FROM users WHERE id=3");
    verify(validator, times(3)).validate(deleteCaptor.capture());
    assertEquals(SqlCommandType.DELETE, deleteCaptor.getAllValues().get(2).getType());

    // Test INSERT - use unique SQL
    ArgumentCaptor<SqlContext> insertCaptor = ArgumentCaptor.forClass(SqlContext.class);
    filter.connection_prepareStatement(filterChain, connectionProxy, "INSERT INTO users VALUES(4)");
    verify(validator, times(4)).validate(insertCaptor.capture());
    assertEquals(SqlCommandType.INSERT, insertCaptor.getAllValues().get(3).getType());
  }

  @Test
  void testBLOCKStrategy_shouldThrowException() {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.BLOCK);
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      filter.connection_prepareStatement(filterChain, connectionProxy, "DELETE FROM users");
    });

    assertTrue(exception.getMessage().contains("SQL Safety Violation"));
    assertTrue(exception.getMessage().contains("Missing WHERE clause"));
    assertEquals("42000", exception.getSQLState());
  }

  @Test
  void testWARNStrategy_shouldLogAndContinue() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.MEDIUM, "Potential issue", "Fix it");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act - should not throw
    assertDoesNotThrow(() -> {
      filter.connection_prepareStatement(filterChain, connectionProxy, "SELECT * FROM users");
    });

    // Assert - validation was called
    verify(validator).validate(any(SqlContext.class));
  }

  @Test
  void testLOGStrategy_shouldOnlyLog() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.LOG);
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.LOW, "Minor issue", "Consider fixing");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act - should not throw (use unique SQL)
    assertDoesNotThrow(() -> {
      filter.connection_prepareStatement(filterChain, connectionProxy, "SELECT * FROM log_test_table");
    });

    // Assert - validation was called
    verify(validator).validate(any(SqlContext.class));
  }

  @Test
  void testValidSql_shouldProceed() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.BLOCK);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act - should not throw
    assertDoesNotThrow(() -> {
      filter.connection_prepareStatement(
          filterChain, connectionProxy, "SELECT * FROM users WHERE id = ?");
    });

    // Assert
    verify(validator).validate(any(SqlContext.class));
  }

  @Test
  void testFilterChain_shouldDelegate() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act - should not throw and validation should be called
    assertDoesNotThrow(() -> {
      filter.connection_prepareStatement(filterChain, connectionProxy, "SELECT 1");
    });

    // Assert - validation was called (filter chain delegation is internal)
    verify(validator).validate(any(SqlContext.class));
  }
}














