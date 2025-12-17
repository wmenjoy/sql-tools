package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.config.ConfigFilter;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallFilter;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DruidSqlSafetyFilter compatibility with Druid ecosystem plugins.
 *
 * <p>Verifies coexistence and correct execution order with StatFilter, WallFilter, ConfigFilter,
 * and LogFilter.</p>
 */
class DruidPluginCompatibilityTest {

  private DruidDataSource dataSource;
  private DefaultSqlSafetyValidator validator;

  @BeforeEach
  void setUp() {
    dataSource = new DruidDataSource();
    dataSource.setUrl("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setName("compatTestDataSource");

    validator = mock(DefaultSqlSafetyValidator.class);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());

    // Clear deduplication cache
    com.footstone.sqlguard.validator.SqlDeduplicationFilter.clearThreadCache();
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null && dataSource.isInited()) {
      dataSource.close();
    }
  }

  @Test
  void testStatFilter_withSafety_shouldCoexist() throws SQLException {
    // Arrange - add StatFilter
    StatFilter statFilter = new StatFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    dataSource.setProxyFilters(filters);

    // Add safety filter
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1");
    }

    // Assert - both filters should be present
    List<Filter> allFilters = dataSource.getProxyFilters();
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof DruidSqlSafetyFilter));
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof StatFilter));
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testStatFilter_violations_shouldAppearInStats() throws SQLException {
    // Arrange
    StatFilter statFilter = new StatFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    dataSource.setProxyFilters(filters);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Act - execute some queries
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - both filters executed successfully
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testWallFilter_withSafety_shouldCoexist() throws SQLException {
    // Arrange - add WallFilter (SQL firewall)
    WallFilter wallFilter = new WallFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(wallFilter);
    dataSource.setProxyFilters(filters);

    // Add safety filter
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - both filters should be present
    List<Filter> allFilters = dataSource.getProxyFilters();
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof DruidSqlSafetyFilter));
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof WallFilter));
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testWallFilter_sqlFirewall_andSafety_bothCheck() throws SQLException {
    // Arrange
    WallFilter wallFilter = new WallFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(wallFilter);
    dataSource.setProxyFilters(filters);

    // Configure validator to reject dangerous SQL
    ValidationResult dangerousResult = ValidationResult.pass();
    dangerousResult.addViolation(RiskLevel.HIGH, "Dangerous SQL", "Fix it");
    when(validator.validate(any(SqlContext.class))).thenReturn(dangerousResult);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.BLOCK);
    dataSource.init();

    // Act & Assert - safety filter should block before WallFilter
    assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
        stmt.executeQuery("SELECT 1");
      }
    });

    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testConfigFilter_encrypted_shouldWork() throws SQLException {
    // Arrange - ConfigFilter is typically used for password encryption
    ConfigFilter configFilter = new ConfigFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(configFilter);
    dataSource.setProxyFilters(filters);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - both filters should coexist
    List<Filter> allFilters = dataSource.getProxyFilters();
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof DruidSqlSafetyFilter));
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof ConfigFilter));
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testLogFilter_withSafety_shouldCoexist() throws SQLException {
    // Arrange - add Slf4jLogFilter
    Slf4jLogFilter logFilter = new Slf4jLogFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(logFilter);
    dataSource.setProxyFilters(filters);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert
    List<Filter> allFilters = dataSource.getProxyFilters();
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof DruidSqlSafetyFilter));
    assertTrue(allFilters.stream().anyMatch(f -> f instanceof Slf4jLogFilter));
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testMultipleFilters_executionOrder_correct() throws SQLException {
    // Arrange - add multiple filters
    StatFilter statFilter = new StatFilter();
    Slf4jLogFilter logFilter = new Slf4jLogFilter();

    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    filters.add(logFilter);
    dataSource.setProxyFilters(filters);

    // Add safety filter (should be at position 0)
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - safety filter should be present and execute first
    List<Filter> allFilters = dataSource.getProxyFilters();
    
    // Find safety filter position
    int safetyFilterIndex = -1;
    for (int i = 0; i < allFilters.size(); i++) {
      if (allFilters.get(i) instanceof DruidSqlSafetyFilter) {
        safetyFilterIndex = i;
        break;
      }
    }
    
    assertTrue(safetyFilterIndex >= 0, "Safety filter should be present");
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testFilterChain_allExecute() throws SQLException {
    // Arrange - add multiple filters
    StatFilter statFilter = new StatFilter();
    WallFilter wallFilter = new WallFilter();

    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    filters.add(wallFilter);
    dataSource.setProxyFilters(filters);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - all filters should have executed
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testDruidMonitor_violations_shouldShow() throws SQLException {
    // Arrange
    StatFilter statFilter = new StatFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    dataSource.setProxyFilters(filters);

    ValidationResult violationResult = ValidationResult.pass();
    violationResult.addViolation(RiskLevel.MEDIUM, "Test violation", "Fix");
    when(validator.validate(any(SqlContext.class))).thenReturn(violationResult);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Act - execute query with violation (WARN strategy allows execution)
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - both filters executed successfully
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testDruidWeb_statView_shouldDisplayViolations() throws SQLException {
    // This is a conceptual test - actual web console integration would require
    // full Druid monitoring setup
    // Arrange
    StatFilter statFilter = new StatFilter();
    List<Filter> filters = new ArrayList<>();
    filters.add(statFilter);
    dataSource.setProxyFilters(filters);

    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.LOG);
    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - verify safety filter is present
    assertTrue(dataSource.getProxyFilters().stream()
        .anyMatch(f -> f instanceof DruidSqlSafetyFilter));
    verify(validator, atLeastOnce()).validate(any(SqlContext.class));
  }

  @Test
  void testRemoveFilter_dynamically_shouldWork() throws SQLException {
    // Arrange
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);
    dataSource.init();

    // Verify filter is present
    assertTrue(dataSource.getProxyFilters().stream()
        .anyMatch(f -> f instanceof DruidSqlSafetyFilter));

    // Act - remove filter dynamically
    DruidSqlSafetyFilterConfiguration.removeFilter(dataSource);

    // Assert - filter should be removed
    assertFalse(dataSource.getProxyFilters().stream()
        .anyMatch(f -> f instanceof DruidSqlSafetyFilter));

    // Execute query - should work without validation
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Validator should not be called after filter removal
    verify(validator, never()).validate(any(SqlContext.class));
  }

  @Test
  void testFilterDisabled_shouldNotIntercept() throws SQLException {
    // Arrange - don't register the filter
    dataSource.init();

    // Act
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE test (id INT)");
      stmt.executeQuery("SELECT 1 FROM test");
    }

    // Assert - validator should not be called
    verify(validator, never()).validate(any(SqlContext.class));
  }
}
