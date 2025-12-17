package com.footstone.sqlguard.interceptor.druid;

import static org.mockito.Mockito.mock;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Debug test to understand Druid filter behavior.
 */
class DebugFilterRemovalTest {

  @Test
  void debugFilterRemoval() {
    DefaultSqlSafetyValidator validator = mock(DefaultSqlSafetyValidator.class);
    DruidDataSource ds = new DruidDataSource();

    System.out.println("=== Initial state ===");
    System.out.println("Filters: " + ds.getProxyFilters());

    System.out.println("\n=== After registration ===");
    DruidSqlSafetyFilterConfiguration.registerFilter(ds, validator, ViolationStrategy.WARN);
    List<Filter> after = ds.getProxyFilters();
    System.out.println("Filters: " + after);
    System.out.println("Size: " + (after != null ? after.size() : "null"));
    if (after != null) {
      for (int i = 0; i < after.size(); i++) {
        System.out.println("  [" + i + "] " + after.get(i).getClass().getName());
      }
    }

    System.out.println("\n=== After removal ===");
    DruidSqlSafetyFilterConfiguration.removeFilter(ds);
    List<Filter> afterRemoval = ds.getProxyFilters();
    System.out.println("Filters: " + afterRemoval);
    System.out.println("Size: " + (afterRemoval != null ? afterRemoval.size() : "null"));
    if (afterRemoval != null) {
      for (int i = 0; i < afterRemoval.size(); i++) {
        System.out.println("  [" + i + "] " + afterRemoval.get(i).getClass().getName());
      }
    }

    System.out.println("\n=== Try direct list manipulation ===");
    List<Filter> filters = ds.getProxyFilters();
    System.out.println("List class: " + (filters != null ? filters.getClass().getName() : "null"));
    System.out.println("Is same instance as before? " + (filters == after));
    
    // Try to clear the list directly
    if (filters != null) {
      try {
        filters.clear();
        System.out.println("After clear(): " + ds.getProxyFilters());
      } catch (Exception e) {
        System.out.println("clear() failed: " + e.getClass().getName() + ": " + e.getMessage());
      }
    }
  }
}
