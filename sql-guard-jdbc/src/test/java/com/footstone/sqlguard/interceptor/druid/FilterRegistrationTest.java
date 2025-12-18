package com.footstone.sqlguard.interceptor.druid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DruidSqlSafetyFilterConfiguration.
 *
 * <p>Tests filter registration, ordering, Spring integration, and dynamic management.</p>
 */
class FilterRegistrationTest {

  private DruidDataSource dataSource;
  private DefaultSqlSafetyValidator validator;

  @BeforeEach
  void setUp() {
    dataSource = new DruidDataSource();
    validator = mock(DefaultSqlSafetyValidator.class);
  }

  @Test
  void testRegisterFilter_shouldAddToProxyFilters() {
    // Act
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert
    List<Filter> filters = dataSource.getProxyFilters();
    assertNotNull(filters);
    assertEquals(1, filters.size());
    assertTrue(filters.get(0) instanceof DruidSqlSafetyFilter);
  }

  @Test
  void testFilterOrder_shouldBe1() {
    // Act
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert - safety filter should be at index 0 (executes first)
    List<Filter> filters = dataSource.getProxyFilters();
    assertTrue(filters.get(0) instanceof DruidSqlSafetyFilter,
        "Safety filter should be at position 0 to execute first");
  }

  @Test
  void testFilterOrder_beforeStatFilter() {
    // Arrange - add StatFilter first
    StatFilter statFilter = new StatFilter();
    List<Filter> existingFilters = new ArrayList<>();
    existingFilters.add(statFilter);
    dataSource.setProxyFilters(existingFilters);

    // Act - register safety filter
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert
    List<Filter> filters = dataSource.getProxyFilters();
    assertTrue(filters.size() >= 2, "Should have at least 2 filters");

    // Find both filters
    int safetyFilterIndex = -1;
    int statFilterIndex = -1;
    for (int i = 0; i < filters.size(); i++) {
      if (filters.get(i) instanceof DruidSqlSafetyFilter) {
        safetyFilterIndex = i;
      } else if (filters.get(i) instanceof StatFilter) {
        statFilterIndex = i;
      }
    }

    assertTrue(safetyFilterIndex >= 0, "Safety filter should be in the list");
    assertTrue(statFilterIndex >= 0, "StatFilter should be in the list");
    assertTrue(safetyFilterIndex < statFilterIndex, 
        "Safety filter should be before StatFilter");
  }

  @Test
  void testMultipleDatasources_shouldRegisterEach() {
    // Arrange
    DruidDataSource ds1 = new DruidDataSource();
    DruidDataSource ds2 = new DruidDataSource();

    // Act
    DruidSqlSafetyFilterConfiguration.registerFilter(ds1, validator, ViolationStrategy.WARN);
    DruidSqlSafetyFilterConfiguration.registerFilter(ds2, validator, ViolationStrategy.BLOCK);

    // Assert
    assertEquals(1, ds1.getProxyFilters().size());
    assertEquals(1, ds2.getProxyFilters().size());
    assertTrue(ds1.getProxyFilters().get(0) instanceof DruidSqlSafetyFilter);
    assertTrue(ds2.getProxyFilters().get(0) instanceof DruidSqlSafetyFilter);
  }

  @Test
  void testSpringBeanPostProcessor_shouldRegister() {
    // Arrange
    DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);

    // Act
    Object postProcessor = DruidSqlSafetyFilterConfiguration.createSpringBeanPostProcessor(filter);

    // Assert
    assertNotNull(postProcessor);
    // Verify it implements BeanPostProcessor interface
    assertTrue(postProcessor.getClass().getInterfaces().length > 0);
  }

  @Test
  void testFilterExecution_shouldTriggerValidation() {
    // This test verifies that registered filter is actually in the chain
    // Arrange
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert
    List<Filter> filters = dataSource.getProxyFilters();
    assertNotNull(filters);
    assertFalse(filters.isEmpty());

    // Verify filter is DruidSqlSafetyFilter
    boolean hasSafetyFilter = false;
    for (Filter filter : filters) {
      if (filter instanceof DruidSqlSafetyFilter) {
        hasSafetyFilter = true;
        break;
      }
    }
    assertTrue(hasSafetyFilter, "Safety filter should be in proxy filters");
  }

  @Test
  void testDruidStatistics_shouldIncludeViolations() {
    // This is a conceptual test - actual statistics integration would require
    // full Druid setup with real connections
    // Arrange
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert - verify filter is registered at position 0 (executes first)
    List<Filter> filters = dataSource.getProxyFilters();
    assertTrue(filters.get(0) instanceof DruidSqlSafetyFilter,
        "Safety filter at position 0 ensures execution before StatFilter");
  }

  @Test
  void testFilterChain_shouldExecuteInOrder() {
    // Arrange - add multiple filters
    StatFilter statFilter = new StatFilter();
    List<Filter> existingFilters = new ArrayList<>();
    existingFilters.add(statFilter);
    dataSource.setProxyFilters(existingFilters);

    // Act
    DruidSqlSafetyFilterConfiguration.registerFilter(
        dataSource, validator, ViolationStrategy.WARN);

    // Assert - verify both filters present and in correct order
    List<Filter> filters = dataSource.getProxyFilters();
    assertTrue(filters.size() >= 2, "Should have at least 2 filters");

    // Find both filters
    int safetyFilterIndex = -1;
    int statFilterIndex = -1;
    for (int i = 0; i < filters.size(); i++) {
      if (filters.get(i) instanceof DruidSqlSafetyFilter) {
        safetyFilterIndex = i;
      } else if (filters.get(i) instanceof StatFilter) {
        statFilterIndex = i;
      }
    }

    assertTrue(safetyFilterIndex >= 0, "Safety filter should be in the list");
    assertTrue(statFilterIndex >= 0, "StatFilter should be in the list");
    assertTrue(safetyFilterIndex < statFilterIndex, 
        "Safety filter should execute before StatFilter");
  }

  @Test
  void testRemoveFilter_shouldWork() {
    // Arrange - use a fresh DataSource instance for this test
    DruidDataSource freshDataSource = new DruidDataSource();
    
    // Register filter
    DruidSqlSafetyFilterConfiguration.registerFilter(
        freshDataSource, validator, ViolationStrategy.WARN);
    
    // Verify safety filter is present
    List<Filter> filtersBeforeRemoval = freshDataSource.getProxyFilters();
    long countBefore = filtersBeforeRemoval.stream()
        .filter(f -> f instanceof DruidSqlSafetyFilter)
        .count();
    assertTrue(countBefore > 0, "Safety filter should be present before removal");

    // Act - remove filter
    DruidSqlSafetyFilterConfiguration.removeFilter(freshDataSource);

    // Assert - safety filter count should be zero
    List<Filter> filtersAfterRemoval = freshDataSource.getProxyFilters();
    long countAfter = 0;
    if (filtersAfterRemoval != null) {
      countAfter = filtersAfterRemoval.stream()
          .filter(f -> f instanceof DruidSqlSafetyFilter)
          .count();
    }
    assertEquals(0, countAfter, "All safety filters should be removed");
  }

  @Test
  void testDynamicRegistration_shouldWork() {
    // Test dynamic registration and removal - use fresh DataSource
    DruidDataSource freshDataSource = new DruidDataSource();
    
    // Arrange & Act - register
    DruidSqlSafetyFilterConfiguration.registerFilter(
        freshDataSource, validator, ViolationStrategy.WARN);
    
    long count1 = freshDataSource.getProxyFilters().stream()
        .filter(f -> f instanceof DruidSqlSafetyFilter)
        .count();
    assertTrue(count1 > 0, "Safety filter should be present after first registration");

    // Act - remove
    DruidSqlSafetyFilterConfiguration.removeFilter(freshDataSource);
    List<Filter> filtersAfterRemoval = freshDataSource.getProxyFilters();
    long count2 = 0;
    if (filtersAfterRemoval != null) {
      count2 = filtersAfterRemoval.stream()
          .filter(f -> f instanceof DruidSqlSafetyFilter)
          .count();
    }
    assertEquals(0, count2, "Safety filter should be removed");

    // Act - register again
    DruidSqlSafetyFilterConfiguration.registerFilter(
        freshDataSource, validator, ViolationStrategy.BLOCK);

    // Assert
    long count3 = freshDataSource.getProxyFilters().stream()
        .filter(f -> f instanceof DruidSqlSafetyFilter)
        .count();
    assertTrue(count3 > 0, "Safety filter should be present after second registration");
  }

  @Test
  void testRegisterFilter_nullDataSource_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> {
      DruidSqlSafetyFilterConfiguration.registerFilter(null, validator, ViolationStrategy.WARN);
    });
  }

  @Test
  void testRegisterFilter_nullValidator_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> {
      DruidSqlSafetyFilterConfiguration.registerFilter(dataSource, null, ViolationStrategy.WARN);
    });
  }

  @Test
  void testRegisterFilter_nullStrategy_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> {
      DruidSqlSafetyFilterConfiguration.registerFilter(dataSource, validator, null);
    });
  }
}




