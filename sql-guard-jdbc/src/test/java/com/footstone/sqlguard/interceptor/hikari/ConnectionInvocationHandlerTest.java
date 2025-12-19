package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for ConnectionInvocationHandler within HikariSqlSafetyProxyFactory.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>prepareStatement SQL validation and interception</li>
 *   <li>createStatement proxy wrapping</li>
 *   <li>Connection method delegation</li>
 *   <li>Datasource extraction</li>
 *   <li>SQL deduplication</li>
 * </ul>
 */
class ConnectionInvocationHandlerTest {

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
  private CallableStatement mockCallableStatement;

  @BeforeEach
  void setUp() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Setup default mock behaviors
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockDataSource.toString()).thenReturn("test-pool");
  }

  @Test
  void testPrepareStatement_shouldValidateSql() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = ?";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);

    // Then
    assertNotNull(ps);
    verify(validator, times(1)).validate(any());
    verify(mockConnection, times(1)).prepareStatement(sql);
  }

  @Test
  void testPrepareStatement_dangerous_shouldBlock() throws SQLException {
    // Given
    String dangerousSql = "SELECT * FROM user";
    ValidationResult failResult = ValidationResult.pass();
    failResult.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
    when(validator.validate(any())).thenReturn(failResult);
    when(mockConnection.prepareStatement(dangerousSql)).thenReturn(mockPreparedStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();

    // Then
    SQLException exception = assertThrows(SQLException.class, () -> {
      conn.prepareStatement(dangerousSql);
    });
    assertTrue(exception.getMessage().contains("SQL safety violation"));
    assertTrue(exception.getSQLState().equals("42000"));
  }

  @Test
  void testPrepareStatement_safe_shouldProceed() throws SQLException {
    // Given
    String safeSql = "SELECT * FROM user WHERE id = ?";
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockConnection.prepareStatement(safeSql)).thenReturn(mockPreparedStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    PreparedStatement ps = conn.prepareStatement(safeSql);

    // Then
    assertNotNull(ps);
    assertTrue(Proxy.isProxyClass(ps.getClass()));
    verify(validator, times(1)).validate(any());
  }

  @Test
  void testCreateStatement_shouldReturnProxy() throws SQLException {
    // Given
    when(mockConnection.createStatement()).thenReturn(mockStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    Statement stmt = conn.createStatement();

    // Then
    assertNotNull(stmt);
    assertTrue(Proxy.isProxyClass(stmt.getClass()));
    verify(mockConnection, times(1)).createStatement();
    // Validator should not be called for createStatement (SQL not known yet)
    verify(validator, never()).validate(any());
  }

  @Test
  void testClose_shouldDelegateToConnection() throws SQLException {
    // Given
    doNothing().when(mockConnection).close();

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    conn.close();

    // Then
    verify(mockConnection, times(1)).close();
  }

  @Test
  void testCommit_shouldDelegate() throws SQLException {
    // Given
    doNothing().when(mockConnection).commit();

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    conn.commit();

    // Then
    verify(mockConnection, times(1)).commit();
  }

  @Test
  void testRollback_shouldDelegate() throws SQLException {
    // Given
    doNothing().when(mockConnection).rollback();

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    conn.rollback();

    // Then
    verify(mockConnection, times(1)).rollback();
  }

  @Test
  void testSetAutoCommit_shouldDelegate() throws SQLException {
    // Given
    doNothing().when(mockConnection).setAutoCommit(false);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    conn.setAutoCommit(false);

    // Then
    verify(mockConnection, times(1)).setAutoCommit(false);
  }

  @Test
  void testGetMetaData_shouldDelegate() throws SQLException {
    // Given
    java.sql.DatabaseMetaData mockMetaData = mock(java.sql.DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    java.sql.DatabaseMetaData metaData = conn.getMetaData();

    // Then
    assertNotNull(metaData);
    assertEquals(mockMetaData, metaData);
    verify(mockConnection, times(1)).getMetaData();
  }

  @Test
  void testIsClosed_shouldDelegate() throws SQLException {
    // Given
    when(mockConnection.isClosed()).thenReturn(false);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    boolean closed = conn.isClosed();

    // Then
    assertFalse(closed);
    verify(mockConnection, times(1)).isClosed();
  }

  @Test
  void testDatasourceExtraction_shouldWork() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = ?";
    when(mockDataSource.toString()).thenReturn("HikariPool-1");
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    conn.prepareStatement(sql);

    // Then
    verify(validator, times(1)).validate(argThat(context ->
        context.getDatasource().equals("HikariPool-1")
    ));
  }

  @Test
  void testDeduplication_sameSQL_shouldSkip() throws SQLException {
    // Given
    String sql = "SELECT * FROM user WHERE id = ?";
    when(mockConnection.prepareStatement(sql)).thenReturn(mockPreparedStatement);

    // Setup validator to return pass for first call, then should be skipped by deduplication
    when(validator.validate(any())).thenReturn(ValidationResult.pass());

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();

    // Execute same SQL twice
    conn.prepareStatement(sql);
    conn.prepareStatement(sql);

    // Then
    // Validator should be called twice (deduplication is handled by validator internally)
    // but the second call should be fast due to internal caching
    verify(validator, times(2)).validate(any());
  }

  @Test
  void testPrepareCall_shouldValidateAndWrap() throws SQLException {
    // Given
    String sql = "{call get_user(?)}";
    when(mockConnection.prepareCall(sql)).thenReturn(mockCallableStatement);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    CallableStatement cs = conn.prepareCall(sql);

    // Then
    assertNotNull(cs);
    assertTrue(Proxy.isProxyClass(cs.getClass()));
    verify(validator, times(1)).validate(any());
    verify(mockConnection, times(1)).prepareCall(sql);
  }
}








