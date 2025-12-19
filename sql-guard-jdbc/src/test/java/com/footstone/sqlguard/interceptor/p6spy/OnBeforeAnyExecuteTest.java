package com.footstone.sqlguard.interceptor.p6spy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Detailed tests for onBeforeAnyExecute implementation.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>SQL extraction with parameter values substituted</li>
 *   <li>SQL type detection for all command types</li>
 *   <li>Datasource extraction from various JDBC URL formats</li>
 *   <li>SqlContext building with correct fields</li>
 *   <li>Violation handling for different strategies</li>
 *   <li>Deduplication behavior</li>
 * </ul>
 */
class OnBeforeAnyExecuteTest {

  @Mock private DefaultSqlSafetyValidator mockValidator;

  @Mock private StatementInformation mockStatementInfo;

  @Mock private ConnectionInformation mockConnectionInfo;

  private P6SpySqlSafetyListener listener;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  /**
   * Test 1: Extract SQL with values should get parameter-substituted SQL.
   */
  @Test
  void testExtractSqlWithValues_shouldGetSubstituted() throws SQLException {
    // Arrange
    String sqlWithValues = "SELECT * FROM users WHERE id = 123 AND name = 'Alice'";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sqlWithValues);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(mockValidator).validate(contextCaptor.capture());

    SqlContext capturedContext = contextCaptor.getValue();
    assertEquals(sqlWithValues, capturedContext.getSql());
  }

  /**
   * Test 2: Extract SQL with values from PreparedStatement should substitute parameters.
   */
  @Test
  void testExtractSqlWithValues_prepared_shouldSubstituteParams() throws SQLException {
    // Arrange - P6Spy substitutes ? with actual values
    String sqlWithValues = "UPDATE users SET status = 'active' WHERE id = 456";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sqlWithValues);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.WARN);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(mockValidator).validate(contextCaptor.capture());

    SqlContext capturedContext = contextCaptor.getValue();
    assertEquals(sqlWithValues, capturedContext.getSql());
    assertFalse(capturedContext.getSql().contains("?")); // No placeholders
  }

  /**
   * Test 3: Extract SQL with values from Statement should get original SQL.
   */
  @Test
  void testExtractSqlWithValues_statement_shouldGetOriginal() throws SQLException {
    // Arrange
    String sql = "DELETE FROM logs WHERE created_at < '2024-01-01'";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:postgresql://localhost:5432/testdb");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(mockValidator).validate(contextCaptor.capture());

    SqlContext capturedContext = contextCaptor.getValue();
    assertEquals(sql, capturedContext.getSql());
  }

  /**
   * Test 4: Detect SQL type - SELECT should return SELECT.
   */
  @Test
  void testDetectSqlType_SELECT_shouldReturn() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("SELECT * FROM users"));
    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("  select id, name from products"));
    assertEquals(
        SqlCommandType.SELECT, listener.detectSqlType("\n\tSELECT COUNT(*) FROM orders"));
  }

  /**
   * Test 5: Detect SQL type - UPDATE should return UPDATE.
   */
  @Test
  void testDetectSqlType_UPDATE_shouldReturn() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.UPDATE, listener.detectSqlType("UPDATE users SET status = 'active'"));
    assertEquals(SqlCommandType.UPDATE, listener.detectSqlType("  update products set price = 100"));
    assertEquals(
        SqlCommandType.UPDATE, listener.detectSqlType("\n\tUPDATE orders SET status = 'shipped'"));
  }

  /**
   * Test 6: Detect SQL type - DELETE should return DELETE.
   */
  @Test
  void testDetectSqlType_DELETE_shouldReturn() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.DELETE, listener.detectSqlType("DELETE FROM users WHERE id = 1"));
    assertEquals(SqlCommandType.DELETE, listener.detectSqlType("  delete from logs"));
    assertEquals(
        SqlCommandType.DELETE, listener.detectSqlType("\n\tDELETE FROM temp_data WHERE age > 30"));
  }

  /**
   * Test 7: Detect SQL type - INSERT should return INSERT.
   */
  @Test
  void testDetectSqlType_INSERT_shouldReturn() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(
        SqlCommandType.INSERT, listener.detectSqlType("INSERT INTO users (name) VALUES ('test')"));
    assertEquals(
        SqlCommandType.INSERT, listener.detectSqlType("  insert into products values (1, 'item')"));
    assertEquals(
        SqlCommandType.INSERT,
        listener.detectSqlType("\n\tINSERT INTO orders (user_id) VALUES (123)"));
  }

  /**
   * Test 8: Extract datasource from URL - MySQL format.
   */
  @Test
  void testExtractDatasource_fromUrl_shouldParse() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // MySQL
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");
    assertEquals("mydb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // PostgreSQL
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:postgresql://localhost:5432/testdb");
    assertEquals("testdb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // H2 in-memory
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");
    assertEquals("test", listener.extractDatasourceFromUrl(mockStatementInfo));

    // H2 file
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:file:/data/mydb");
    assertEquals("mydb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // Oracle
    when(mockConnectionInfo.getUrl())
        .thenReturn("jdbc:p6spy:oracle:thin:@localhost:1521:orcl");
    assertEquals("orcl", listener.extractDatasourceFromUrl(mockStatementInfo));

    // With query parameters
    when(mockConnectionInfo.getUrl())
        .thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
    assertEquals("mydb", listener.extractDatasourceFromUrl(mockStatementInfo));
  }

  /**
   * Test 9: Build SqlContext should populate all fields correctly.
   */
  @Test
  void testBuildSqlContext_shouldPopulateFields() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 123";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(mockValidator).validate(contextCaptor.capture());

    SqlContext capturedContext = contextCaptor.getValue();
    assertEquals(sql, capturedContext.getSql());
    assertEquals(SqlCommandType.SELECT, capturedContext.getType());
    assertTrue(capturedContext.getMapperId().contains("jdbc.p6spy"));
    assertEquals("mydb", capturedContext.getDatasource());
  }

  /**
   * Test 10: Handle violation with BLOCK strategy should throw SQLException.
   */
  @Test
  void testHandleViolation_BLOCK_shouldThrow() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(failResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act & Assert
    SQLException exception =
        assertThrows(SQLException.class, () -> listener.onBeforeAnyExecute(mockStatementInfo));

    assertTrue(exception.getMessage().contains("SQL Safety Violation"));
    assertTrue(exception.getMessage().contains("Missing WHERE clause"));
    assertEquals("42000", exception.getSQLState());
  }

  /**
   * Test 11: Handle violation with WARN strategy should log and continue.
   */
  @Test
  void testHandleViolation_WARN_shouldLog() throws SQLException {
    // Arrange
    String sql = "DELETE FROM orders";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(failResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.WARN);

    // Act - should not throw
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 12: Deduplication should prevent double validation.
   */
  @Test
  void testDeduplication_shouldPreventDouble() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 789";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    // Use real deduplication filter
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100L);
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG, filter);

    // Act - first call
    listener.onBeforeAnyExecute(mockStatementInfo);
    verify(mockValidator, times(1)).validate(any(SqlContext.class));

    // Act - second call within TTL should skip
    listener.onBeforeAnyExecute(mockStatementInfo);
    verify(mockValidator, times(1)).validate(any(SqlContext.class)); // Still 1

    // Clean up
    SqlDeduplicationFilter.clearThreadCache();
  }

  /**
   * Test: Case-insensitive SQL type detection.
   */
  @Test
  void testDetectSqlType_caseInsensitive() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("select * from users"));
    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("SELECT * FROM users"));
    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("SeLeCt * FrOm users"));

    assertEquals(SqlCommandType.UPDATE, listener.detectSqlType("update users set name = 'test'"));
    assertEquals(SqlCommandType.DELETE, listener.detectSqlType("delete from users"));
    assertEquals(SqlCommandType.INSERT, listener.detectSqlType("insert into users values (1)"));
  }

  /**
   * Test: SQL type detection with leading whitespace.
   */
  @Test
  void testDetectSqlType_withWhitespace() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("   SELECT * FROM users"));
    assertEquals(SqlCommandType.UPDATE, listener.detectSqlType("\t\tUPDATE users SET x = 1"));
    assertEquals(SqlCommandType.DELETE, listener.detectSqlType("\n\nDELETE FROM users"));
    assertEquals(SqlCommandType.INSERT, listener.detectSqlType("  \t\n  INSERT INTO users"));
  }

  /**
   * Test: Extract datasource with null connection info.
   */
  @Test
  void testExtractDatasource_nullConnectionInfo() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    when(mockStatementInfo.getConnectionInformation()).thenReturn(null);
    assertEquals("unknown", listener.extractDatasourceFromUrl(mockStatementInfo));
  }

  /**
   * Test: Extract datasource with null URL.
   */
  @Test
  void testExtractDatasource_nullUrl() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn(null);
    assertEquals("unknown", listener.extractDatasourceFromUrl(mockStatementInfo));
  }

  /**
   * Test: Extract datasource with malformed URL.
   */
  @Test
  void testExtractDatasource_malformedUrl() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("invalid-url");
    // For malformed URLs without '/' or ':', returns the last part after ':' or "unknown"
    assertEquals("unknown", listener.extractDatasourceFromUrl(mockStatementInfo));
  }
}








