package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
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
 * Unit tests for validateSql method and related helper methods in DruidSqlSafetyFilter.
 *
 * <p>Tests SQL type detection, datasource extraction, SqlContext building, deduplication, and
 * violation handling.</p>
 */
class ValidateSqlMethodTest {

  private DefaultSqlSafetyValidator validator;
  private DruidSqlSafetyFilter filter;
  private ConnectionProxy connectionProxy;
  private DataSourceProxy dataSourceProxy;
  private FilterChain filterChain;
  private StatementProxy statementProxy;

  @BeforeEach
  void setUp() {
    validator = mock(DefaultSqlSafetyValidator.class);
    dataSourceProxy = mock(DataSourceProxy.class);
    connectionProxy = mock(ConnectionProxy.class);
    filterChain = mock(FilterChain.class);
    statementProxy = mock(StatementProxy.class);

    when(connectionProxy.getDirectDataSource()).thenReturn(dataSourceProxy);
    when(dataSourceProxy.getName()).thenReturn("testDataSource");
    when(statementProxy.getConnectionProxy()).thenReturn(connectionProxy);

    // Clear deduplication cache before each test
    com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
  }

  @Test
  void testDetectSqlType_SELECT_shouldReturn() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeQuery(filterChain, statementProxy, "SELECT * FROM users");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.SELECT, captor.getValue().getType());
  }

  @Test
  void testDetectSqlType_UPDATE_shouldReturn() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeUpdate(filterChain, statementProxy, "UPDATE users SET name='test'");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.UPDATE, captor.getValue().getType());
  }

  @Test
  void testDetectSqlType_DELETE_shouldReturn() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeUpdate(filterChain, statementProxy, "DELETE FROM users WHERE id=1");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.DELETE, captor.getValue().getType());
  }

  @Test
  void testDetectSqlType_INSERT_shouldReturn() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeUpdate(filterChain, statementProxy, "INSERT INTO users VALUES(1, 'test')");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.INSERT, captor.getValue().getType());
  }

  @Test
  void testDetectSqlType_caseInsensitive_shouldWork() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act - test lowercase
    filter.statement_executeQuery(filterChain, statementProxy, "select * from users");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.SELECT, captor.getValue().getType());
  }

  @Test
  void testDetectSqlType_withComments_shouldDetect() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act - SQL with leading whitespace
    filter.statement_executeQuery(filterChain, statementProxy, "  \n  SELECT * FROM users");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.SELECT, captor.getValue().getType());
  }

  @Test
  void testExtractDatasourceName_shouldGet() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(dataSourceProxy.getName()).thenReturn("myCustomDataSource");
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeQuery(filterChain, statementProxy, "SELECT 1");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals("myCustomDataSource", captor.getValue().getDatasource());
  }

  @Test
  void testExtractDatasourceName_null_shouldReturnDefault() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(dataSourceProxy.getName()).thenReturn(null);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeQuery(filterChain, statementProxy, "SELECT 1");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals("default", captor.getValue().getDatasource());
  }

  @Test
  void testBuildSqlContext_shouldPopulateFields() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(dataSourceProxy.getName()).thenReturn("testDS");
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
    String sql = "SELECT * FROM users WHERE id = ?";

    // Act
    filter.connection_prepareStatement(filterChain, connectionProxy, sql);

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    SqlContext context = captor.getValue();

    assertEquals(sql, context.getSql());
    assertEquals(SqlCommandType.SELECT, context.getType());
    assertEquals("jdbc.druid:testDS", context.getMapperId());
    assertEquals("testDS", context.getDatasource());
  }

  @Test
  void testMapperId_shouldIncludeDatasource() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(dataSourceProxy.getName()).thenReturn("productionDB");
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Act
    filter.statement_executeQuery(filterChain, statementProxy, "SELECT 1");

    // Assert
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals("jdbc.druid:productionDB", captor.getValue().getMapperId());
  }

  @Test
  void testDeduplication_sameSQL_shouldSkip() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
    String sql = "SELECT * FROM dedup_test";

    // Act - execute same SQL twice within TTL window
    filter.statement_executeQuery(filterChain, statementProxy, sql);
    filter.statement_executeQuery(filterChain, statementProxy, sql);

    // Assert - validator should be called only once (second call deduplicated)
    verify(validator, times(1)).validate(any(SqlContext.class));
  }

  @Test
  void testHandleViolation_shouldFormatMessage() throws SQLException {
    // Arrange
    filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.BLOCK);
    ValidationResult result = ValidationResult.pass();
    result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    result.addViolation(RiskLevel.MEDIUM, "SELECT *", "Specify columns");
    when(validator.validate(any(SqlContext.class))).thenReturn(result);

    // Act & Assert
    SQLException exception = assertThrows(SQLException.class, () -> {
      filter.statement_executeUpdate(filterChain, statementProxy, "DELETE FROM users");
    });

    // Verify message format
    String message = exception.getMessage();
    assertTrue(message.contains("SQL Safety Violation"));
    assertTrue(message.contains("Druid Filter"));
    assertTrue(message.contains("Datasource: testDataSource"));
    assertTrue(message.contains("Risk: HIGH"));
    assertTrue(message.contains("Missing WHERE clause"));
    assertTrue(message.contains("SELECT *"));
  }
}














