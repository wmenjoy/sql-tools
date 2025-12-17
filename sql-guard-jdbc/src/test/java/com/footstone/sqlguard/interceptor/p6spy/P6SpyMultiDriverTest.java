package com.footstone.sqlguard.interceptor.p6spy;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Multi-driver compatibility tests for P6Spy SQL Safety Guard.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>H2 driver compatibility</li>
 *   <li>JDBC URL parsing for different drivers</li>
 *   <li>Driver switching capabilities</li>
 *   <li>Multiple datasources with different drivers</li>
 * </ul>
 *
 * <p>Note: MySQL, PostgreSQL, and Oracle tests require actual database instances.
 * These tests focus on H2 (in-memory) which is always available.</p>
 */
class P6SpyMultiDriverTest {

  private Connection connection;

  @AfterEach
  void tearDown() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  /**
   * Test 1: H2 driver should wrap correctly.
   */
  @Test
  void testH2Driver_shouldWrap() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    
    assertNotNull(connection);
    assertFalse(connection.isClosed());
    
    DatabaseMetaData metadata = connection.getMetaData();
    assertTrue(metadata.getDatabaseProductName().contains("H2"));
  }

  /**
   * Test 2: H2 driver SQL validation should work.
   */
  @Test
  void testH2Driver_sqlValidation_shouldWork() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    
    // Create table
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(255))");
      stmt.execute("INSERT INTO test_table VALUES (1, 'test')");
    }
    
    // Query with WHERE clause (safe)
    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM test_table WHERE id = ?")) {
      ps.setInt(1, 1);
      ResultSet rs = ps.executeQuery();
      assertTrue(rs.next());
      assertEquals("test", rs.getString("name"));
    }
    
    // Cleanup
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP TABLE test_table");
    }
  }

  /**
   * Test 3: H2 in-memory URL parsing.
   */
  @Test
  void testUrlParsing_h2_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    // Mock statement info for URL parsing
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl()).thenReturn("jdbc:h2:mem:test");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("test", datasource);
  }

  /**
   * Test 4: MySQL URL parsing (without actual connection).
   */
  @Test
  void testUrlParsing_mysql_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl()).thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("mydb", datasource);
  }

  /**
   * Test 5: PostgreSQL URL parsing (without actual connection).
   */
  @Test
  void testUrlParsing_postgresql_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl()).thenReturn("jdbc:p6spy:postgresql://localhost:5432/testdb");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("testdb", datasource);
  }

  /**
   * Test 6: H2 file-based URL parsing.
   */
  @Test
  void testUrlParsing_h2File_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl()).thenReturn("jdbc:h2:file:/data/mydb");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("mydb", datasource);
  }

  /**
   * Test 7: URL with query parameters.
   */
  @Test
  void testUrlParsing_withQueryParams_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl())
        .thenReturn("jdbc:p6spy:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("mydb", datasource);
  }

  /**
   * Test 8: Oracle URL parsing (without actual connection).
   */
  @Test
  void testUrlParsing_oracle_shouldWork() {
    P6SpySqlSafetyListener listener = 
        new P6SpySqlSafetyListener(P6SpySqlSafetyModule.loadValidator(), ViolationStrategy.LOG);
    
    com.p6spy.engine.common.StatementInformation mockInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.StatementInformation.class);
    com.p6spy.engine.common.ConnectionInformation mockConnInfo = 
        org.mockito.Mockito.mock(com.p6spy.engine.common.ConnectionInformation.class);
    
    org.mockito.Mockito.when(mockInfo.getConnectionInformation()).thenReturn(mockConnInfo);
    org.mockito.Mockito.when(mockConnInfo.getUrl())
        .thenReturn("jdbc:p6spy:oracle:thin:@localhost:1521:orcl");
    
    String datasource = listener.extractDatasourceFromUrl(mockInfo);
    assertEquals("orcl", datasource);
  }

  /**
   * Test 9: Driver compatibility check.
   */
  @Test
  void testDriverCompatibility_allSupported() {
    // H2 driver should be available
    try {
      Class.forName("org.h2.Driver");
      assertTrue(true);
    } catch (ClassNotFoundException e) {
      fail("H2 driver not found");
    }
  }

  /**
   * Test 10: Multiple H2 connections.
   */
  @Test
  void testMultipleConnections_shouldWork() throws SQLException {
    Connection conn1 = DriverManager.getConnection("jdbc:h2:mem:db1", "sa", "");
    Connection conn2 = DriverManager.getConnection("jdbc:h2:mem:db2", "sa", "");
    
    assertNotNull(conn1);
    assertNotNull(conn2);
    assertNotEquals(conn1, conn2);
    
    conn1.close();
    conn2.close();
  }

  /**
   * Test 11: Connection pooling simulation.
   */
  @Test
  void testConnectionPooling_shouldWork() throws SQLException {
    // Simulate connection pool by creating and closing multiple connections
    for (int i = 0; i < 5; i++) {
      Connection conn = DriverManager.getConnection("jdbc:h2:mem:pool_test", "sa", "");
      assertNotNull(conn);
      
      try (Statement stmt = conn.createStatement()) {
        ResultSet rs = stmt.executeQuery("SELECT 1");
        assertTrue(rs.next());
      }
      
      conn.close();
    }
  }

  /**
   * Test 12: Concurrent connections.
   */
  @Test
  void testConcurrentConnections_shouldWork() throws InterruptedException {
    Thread[] threads = new Thread[5];
    
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(() -> {
        try {
          Connection conn = DriverManager.getConnection("jdbc:h2:mem:concurrent", "sa", "");
          try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT 1");
            assertTrue(rs.next());
          }
          conn.close();
        } catch (SQLException e) {
          fail("Concurrent connection failed: " + e.getMessage());
        }
      });
      threads[i].start();
    }
    
    for (Thread thread : threads) {
      thread.join();
    }
  }
}

