package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for HikariSqlSafetyProxyFactory.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>DataSource wrapping functionality</li>
 *   <li>Connection proxy creation and behavior</li>
 *   <li>Method interception and delegation</li>
 *   <li>Proxy identity and equality</li>
 * </ul>
 */
class HikariSqlSafetyProxyFactoryTest {

  @Mock
  private DefaultSqlSafetyValidator validator;

  @Mock
  private DataSource mockDataSource;

  @Mock
  private Connection mockConnection;

  @Mock
  private Statement mockStatement;

  @Mock
  private PreparedStatement mockPreparedStatement;

  @Mock
  private DatabaseMetaData mockMetaData;

  @BeforeEach
  void setUp() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Setup default mock behaviors
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockDataSource.getConnection(anyString(), anyString())).thenReturn(mockConnection);
    when(mockDataSource.toString()).thenReturn("test-datasource");
  }

  @Test
  void testWrap_nullDataSource_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyProxyFactory.wrap(null, validator, ViolationStrategy.BLOCK)
    );
    assertEquals("dataSource cannot be null", exception.getMessage());
  }

  @Test
  void testWrap_nullValidator_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyProxyFactory.wrap(mockDataSource, null, ViolationStrategy.BLOCK)
    );
    assertEquals("validator cannot be null", exception.getMessage());
  }

  @Test
  void testWrap_nullStrategy_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyProxyFactory.wrap(mockDataSource, validator, null)
    );
    assertEquals("strategy cannot be null", exception.getMessage());
  }

  @Test
  void testGetProxyConnection_shouldReturnProxy() throws SQLException {
    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);

    // Then
    assertNotNull(wrappedDs);
    assertTrue(Proxy.isProxyClass(wrappedDs.getClass()));
  }

  @Test
  void testProxyConnection_isProxy_shouldBeTrue() throws SQLException {
    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();

    // Then
    assertNotNull(conn);
    assertTrue(Proxy.isProxyClass(conn.getClass()));
  }

  @Test
  void testPrepareStatement_shouldIntercept() throws SQLException {
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
    // Verify validator was called for SQL validation
    verify(validator, atLeastOnce()).validate(any());
    // Verify the underlying connection's prepareStatement was called
    verify(mockConnection, times(1)).prepareStatement(sql);
    // Verify the returned PreparedStatement is a proxy
    assertTrue(Proxy.isProxyClass(ps.getClass()));
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
  }

  @Test
  void testDelegation_otherMethods_shouldWork() throws SQLException {
    // Given
    when(mockConnection.getAutoCommit()).thenReturn(true);
    when(mockConnection.isClosed()).thenReturn(false);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    boolean autoCommit = conn.getAutoCommit();
    boolean closed = conn.isClosed();

    // Then
    assertTrue(autoCommit);
    assertFalse(closed);
    verify(mockConnection, times(1)).getAutoCommit();
    verify(mockConnection, times(1)).isClosed();
  }

  @Test
  void testClose_shouldDelegateCorrectly() throws SQLException {
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
  void testGetMetaData_shouldDelegate() throws SQLException {
    // Given
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);

    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();
    DatabaseMetaData metaData = conn.getMetaData();

    // Then
    assertNotNull(metaData);
    assertEquals(mockMetaData, metaData);
    verify(mockConnection, times(1)).getMetaData();
  }

  @Test
  void testProxyHashCode_shouldWork() throws SQLException {
    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);

    // Then
    assertDoesNotThrow(() -> wrappedDs.hashCode());
  }

  @Test
  void testProxyEquals_shouldWork() throws SQLException {
    // When
    DataSource wrappedDs1 = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);
    DataSource wrappedDs2 = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);

    // Then
    // Different proxy instances should not be equal
    assertNotEquals(wrappedDs1, wrappedDs2);
    // Same proxy instance should be equal to itself (reference equality)
    assertSame(wrappedDs1, wrappedDs1);
  }

  @Test
  void testProxyToString_shouldWork() throws SQLException {
    // When
    DataSource wrappedDs = HikariSqlSafetyProxyFactory.wrap(
        mockDataSource, validator, ViolationStrategy.BLOCK);

    // Then
    assertDoesNotThrow(() -> wrappedDs.toString());
    assertNotNull(wrappedDs.toString());
  }
}



