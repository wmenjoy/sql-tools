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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for P6SpySqlSafetyListener.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>SQL interception and validation</li>
 *   <li>Parameter-substituted SQL extraction</li>
 *   <li>SQL type detection</li>
 *   <li>Violation strategy handling (BLOCK/WARN/LOG)</li>
 *   <li>Deduplication behavior</li>
 *   <li>Edge cases (null SQL, empty SQL)</li>
 * </ul>
 */
class P6SpySqlSafetyListenerTest {

  @Mock private DefaultSqlSafetyValidator mockValidator;

  @Mock private StatementInformation mockStatementInfo;

  @Mock private ConnectionInformation mockConnectionInfo;

  private P6SpySqlSafetyListener listener;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  /**
   * Test 1: onBeforeAnyExecute should validate SQL.
   */
  @Test
  void testOnBeforeAnyExecute_shouldValidate() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 123";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 2: getSqlWithValues should extract parameter-substituted SQL.
   */
  @Test
  void testSqlWithValues_shouldExtractSubstituted() throws SQLException {
    // Arrange
    String sqlWithValues = "SELECT * FROM users WHERE id = 123 AND name = 'John'";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sqlWithValues);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    verify(mockStatementInfo, times(1)).getSqlWithValues();
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 3: PreparedStatement execution should be intercepted.
   */
  @Test
  void testPreparedStatementExecution_shouldIntercept() throws SQLException {
    // Arrange
    String sql = "UPDATE users SET status = 'active' WHERE id = 456";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.WARN);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 4: Statement execution should be intercepted.
   */
  @Test
  void testStatementExecution_shouldIntercept() throws SQLException {
    // Arrange
    String sql = "DELETE FROM logs WHERE created_at < '2024-01-01'";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:postgresql://localhost:5432/testdb");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 5: Batch execution should be intercepted.
   */
  @Test
  void testBatchExecution_shouldIntercept() throws SQLException {
    // Arrange
    String sql = "INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com')";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    listener.onBeforeAnyExecute(mockStatementInfo);

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 6: BLOCK strategy should throw SQLException on violation.
   */
  @Test
  void testBLOCKStrategy_shouldThrowException() throws SQLException {
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
    assertEquals("42000", exception.getSQLState());
  }

  /**
   * Test 7: WARN strategy should log error and continue execution.
   */
  @Test
  void testWARNStrategy_shouldLogAndContinue() throws SQLException {
    // Arrange
    String sql = "DELETE FROM orders";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(failResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.WARN);

    // Act - should not throw exception
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 8: LOG strategy should only log warning.
   */
  @Test
  void testLOGStrategy_shouldOnlyLog() throws SQLException {
    // Arrange
    String sql = "UPDATE products SET price = 0";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.MEDIUM, "Dummy condition detected", "Review WHERE clause");
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(failResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // Act - should not throw exception
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 9: Valid SQL should proceed without violations.
   */
  @Test
  void testValidSql_shouldProceed() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 123 AND status = 'active'";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert
    verify(mockValidator, times(1)).validate(any(SqlContext.class));
  }

  /**
   * Test 10: Deduplication - same SQL should skip validation on second call.
   */
  @Test
  void testDeduplication_sameSQL_shouldSkip() throws SQLException {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = 789";
    when(mockStatementInfo.getSqlWithValues()).thenReturn(sql);
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");

    ValidationResult passResult = ValidationResult.pass();
    when(mockValidator.validate(any(SqlContext.class))).thenReturn(passResult);

    // Use real deduplication filter with short TTL
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter(1000, 100L);
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG, filter);

    // Act - first call should validate
    listener.onBeforeAnyExecute(mockStatementInfo);
    verify(mockValidator, times(1)).validate(any(SqlContext.class));

    // Act - second call within TTL should skip validation
    listener.onBeforeAnyExecute(mockStatementInfo);
    verify(mockValidator, times(1)).validate(any(SqlContext.class)); // Still 1, not 2

    // Clean up
    SqlDeduplicationFilter.clearThreadCache();
  }

  /**
   * Test: detectSqlType should correctly identify SQL command types.
   */
  @Test
  void testDetectSqlType_shouldIdentifyTypes() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    assertEquals(SqlCommandType.SELECT, listener.detectSqlType("SELECT * FROM users"));
    assertEquals(SqlCommandType.UPDATE, listener.detectSqlType("UPDATE users SET name = 'test'"));
    assertEquals(SqlCommandType.DELETE, listener.detectSqlType("DELETE FROM users WHERE id = 1"));
    assertEquals(
        SqlCommandType.INSERT, listener.detectSqlType("INSERT INTO users (name) VALUES ('test')"));
    assertEquals(SqlCommandType.UNKNOWN, listener.detectSqlType("CREATE TABLE users (id INT)"));
    assertEquals(SqlCommandType.UNKNOWN, listener.detectSqlType(""));
    assertEquals(SqlCommandType.UNKNOWN, listener.detectSqlType(null));
  }

  /**
   * Test: extractDatasourceFromUrl should parse JDBC URLs correctly.
   */
  @Test
  void testExtractDatasourceFromUrl_shouldParse() {
    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.LOG);

    // MySQL URL
    when(mockStatementInfo.getConnectionInformation()).thenReturn(mockConnectionInfo);
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");
    assertEquals("mydb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // PostgreSQL URL
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:postgresql://localhost:5432/testdb");
    assertEquals("testdb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // H2 in-memory URL
    when(mockConnectionInfo.getUrl()).thenReturn("jdbc:p6spy:h2:mem:test");
    assertEquals("test", listener.extractDatasourceFromUrl(mockStatementInfo));

    // URL with query parameters
    when(mockConnectionInfo.getUrl())
        .thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb?useSSL=false");
    assertEquals("mydb", listener.extractDatasourceFromUrl(mockStatementInfo));

    // Null connection info
    when(mockStatementInfo.getConnectionInformation()).thenReturn(null);
    assertEquals("unknown", listener.extractDatasourceFromUrl(mockStatementInfo));
  }

  /**
   * Test: null SQL should be skipped.
   */
  @Test
  void testNullSql_shouldSkip() throws SQLException {
    when(mockStatementInfo.getSqlWithValues()).thenReturn(null);

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert - validator should not be called
    verify(mockValidator, never()).validate(any(SqlContext.class));
  }

  /**
   * Test: empty SQL should be skipped.
   */
  @Test
  void testEmptySql_shouldSkip() throws SQLException {
    when(mockStatementInfo.getSqlWithValues()).thenReturn("   ");

    listener = new P6SpySqlSafetyListener(mockValidator, ViolationStrategy.BLOCK);

    // Act
    assertDoesNotThrow(() -> listener.onBeforeAnyExecute(mockStatementInfo));

    // Assert - validator should not be called
    verify(mockValidator, never()).validate(any(SqlContext.class));
  }

  /**
   * Test: constructor should throw IllegalArgumentException for null validator.
   */
  @Test
  void testConstructor_nullValidator_shouldThrow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new P6SpySqlSafetyListener(null, ViolationStrategy.BLOCK));
  }

  /**
   * Test: constructor should throw IllegalArgumentException for null strategy.
   */
  @Test
  void testConstructor_nullStrategy_shouldThrow() {
    assertThrows(
        IllegalArgumentException.class, () -> new P6SpySqlSafetyListener(mockValidator, null));
  }
}








