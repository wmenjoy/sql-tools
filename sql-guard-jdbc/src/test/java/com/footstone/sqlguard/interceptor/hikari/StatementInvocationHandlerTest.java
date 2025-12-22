package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for StatementInvocationHandler within HikariSqlSafetyProxyFactory.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>PreparedStatement execute methods (no SQL parameter, already validated)</li>
 *   <li>Statement execute methods (with SQL parameter, needs validation)</li>
 *   <li>Batch operations</li>
 *   <li>Dangerous SQL blocking</li>
 *   <li>Safe SQL execution</li>
 * </ul>
 */
class StatementInvocationHandlerTest {

  @Mock
  private DefaultSqlSafetyValidator validator;

  @Mock
  private DataSource mockDataSource;

  @Mock
  private Connection mockConnection;

  @Mock
  private PreparedStatement mockPreparedStatement;

  @Mock
  private Statement mockStatement;

  @Mock
  private ResultSet mockResultSet;

  @BeforeEach
  void setUp() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Setup default mock behaviors
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockDataSource.toString()).thenReturn("test-pool");
  }

  @Test
  void testPreparedStatement_execute_shouldDelegate() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = ?";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.execute()).thenReturn(true);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    boolean result = ps.execute();

    // Then
    assertTrue(result);
    verify(mockPreparedStatement, times(1)).execute();
    // Validator called once during prepareStatement, not during execute
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testPreparedStatement_executeQuery_shouldDelegate() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = ?";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    ResultSet rs = ps.executeQuery();

    // Then
    assertNotNull(rs);
    verify(mockPreparedStatement, times(1)).executeQuery();
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testPreparedStatement_executeUpdate_shouldDelegate() throws SQLException {
    // Given
    String sql = "UPDATE user SET name = ? WHERE id = ?";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    int rowCount = ps.executeUpdate();

    // Then
    assertEquals(1, rowCount);
    verify(mockPreparedStatement, times(1)).executeUpdate();
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testPreparedStatement_addBatch_shouldDelegate() throws SQLException {
    // Given
    String sql = "INSERT INTO user (name) VALUES (?)";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
    doNothing().when(mockPreparedStatement).addBatch();

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    ps.addBatch();

    // Then
    verify(mockPreparedStatement, times(1)).addBatch();
    // Validator called once during prepareStatement, not during addBatch
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testPreparedStatement_executeBatch_shouldDelegate() throws SQLException {
    // Given
    String sql = "INSERT INTO user (name) VALUES (?)";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeBatch()).thenReturn(new int[]{1, 1, 1});

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    int[] results = ps.executeBatch();

    // Then
    assertNotNull(results);
    assertEquals(3, results.length);
    verify(mockPreparedStatement, times(1)).executeBatch();
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testStatement_execute_shouldValidate() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = 1";
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.execute(sql)).thenReturn(true);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    boolean result = stmt.execute(sql);

    // Then
    assertTrue(result);
    verify(mockStatement, times(1)).execute(sql);
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testStatement_executeQuery_shouldValidate() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = 1";
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(sql)).thenReturn(mockResultSet);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);

    // Then
    assertNotNull(rs);
    verify(mockStatement, times(1)).executeQuery(sql);
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testStatement_executeUpdate_shouldValidate() throws SQLException {
    // Given
    String sql = "UPDATE user SET name = 'test' WHERE id = 1";
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeUpdate(sql)).thenReturn(1);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    int rowCount = stmt.executeUpdate(sql);

    // Then
    assertEquals(1, rowCount);
    verify(mockStatement, times(1)).executeUpdate(sql);
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testStatement_addBatch_shouldValidate() throws SQLException {
    // Given
    String sql = "INSERT INTO user (name) VALUES ('test')";
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    doNothing().when(mockStatement).addBatch(sql);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    stmt.addBatch(sql);

    // Then
    verify(mockStatement, times(1)).addBatch(sql);
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testStatement_executeBatch_shouldDelegate() throws SQLException {
    // Given
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeBatch()).thenReturn(new int[]{1, 1, 1});

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    int[] results = stmt.executeBatch();

    // Then
    assertNotNull(results);
    assertEquals(3, results.length);
    verify(mockStatement, times(1)).executeBatch();
    // executeBatch itself doesn't validate (individual addBatch calls do)
    verify(validator, never()).validate(any());
  }

  @Test
  void testStatement_dangerous_shouldBlock() throws SQLException {
    // Given
    String dangerousSql = "DELETE FROM user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);
    when(mockConnection.createStatement()).thenReturn(mockStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();

    // Then
    SQLException exception = assertThrows(SQLException.class, () -> {
      stmt.executeUpdate(dangerousSql);
    });
    assertTrue(exception.getMessage().contains("SQL safety violation"));
    assertEquals("42000", exception.getSQLState());
  }

  @Test
  void testStatement_safe_shouldProceed() throws SQLException {
    // Given
    String safeSql = "SELECT * FROM user WHERE id = 1";
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(safeSql)).thenReturn(mockResultSet);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(safeSql);

    // Then
    assertNotNull(rs);
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testBatchOperations_multipleSQL_shouldValidateEach() throws SQLException {
    // Given
    String sql1 = "INSERT INTO user (name) VALUES ('user1')";
    String sql2 = "INSERT INTO user (name) VALUES ('user2')";
    String sql3 = "INSERT INTO user (name) VALUES ('user3')";
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    doNothing().when(mockStatement).addBatch(anyString());

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    stmt.addBatch(sql1);
    stmt.addBatch(sql2);
    stmt.addBatch(sql3);

    // Then
    verify(validator, times(3)).validate(any());
    verify(mockStatement, times(1)).addBatch(sql1);
    verify(mockStatement, times(1)).addBatch(sql2);
    verify(mockStatement, times(1)).addBatch(sql3);
  }

  @Test
  void testClose_shouldDelegate() throws SQLException {
    // Given
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    doNothing().when(mockStatement).close();

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    stmt.close();

    // Then
    verify(mockStatement, times(1)).close();
  }

  @Test
  void testGetResultSet_shouldDelegate() throws SQLException {
    // Given
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockStatement.getResultSet()).thenReturn(mockResultSet);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.getResultSet();

    // Then
    assertNotNull(rs);
    assertEquals(mockResultSet, rs);
    verify(mockStatement, times(1)).getResultSet();
  }
}









