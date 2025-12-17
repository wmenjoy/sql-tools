package com.footstone.sqlguard.interceptor.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration helper for registering DruidSqlSafetyFilter with DruidDataSource.
 *
 * <p>This class provides utility methods for programmatic filter registration and Spring Boot
 * integration. The filter is registered with order=1 to execute before Druid's StatFilter
 * (order=2), allowing violations to be tracked in Druid statistics.</p>
 *
 * <p><strong>Filter Ordering Strategy:</strong></p>
 * <ul>
 *   <li>Druid default filters: ProtocolFilter=1, StatFilter=2</li>
 *   <li>Safety filter order=1 ensures execution before StatFilter</li>
 *   <li>This allows violations to be tracked in Druid statistics</li>
 * </ul>
 *
 * <p><strong>Usage Example (Programmatic):</strong></p>
 * <pre>{@code
 * DruidDataSource dataSource = new DruidDataSource();
 * dataSource.setUrl("jdbc:mysql://localhost:3306/mydb");
 * // ... other datasource configuration
 *
 * DruidSqlSafetyFilterConfiguration.registerFilter(
 *     dataSource,
 *     validator,
 *     ViolationStrategy.WARN
 * );
 * }</pre>
 *
 * <p><strong>Usage Example (Spring Boot):</strong></p>
 * <pre>{@code
 * @Configuration
 * public class DruidConfig {
 *     @Bean
 *     public DruidSqlSafetyFilter druidSqlSafetyFilter(
 *         DefaultSqlSafetyValidator validator) {
 *         return new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
 *     }
 *
 *     @Bean
 *     public BeanPostProcessor druidDataSourcePostProcessor(
 *         DruidSqlSafetyFilter filter) {
 *         return DruidSqlSafetyFilterConfiguration
 *             .createSpringBeanPostProcessor(filter);
 *     }
 * }
 * }</pre>
 *
 * @see DruidSqlSafetyFilter
 * @see DefaultSqlSafetyValidator
 * @see ViolationStrategy
 */
public class DruidSqlSafetyFilterConfiguration {

  // Note: Druid filters don't have explicit ordering mechanism.
  // Filter execution order is determined by their position in the proxy filters list.
  // We add the safety filter at the beginning of the list to ensure it executes first.

  /**
   * Private constructor to prevent instantiation.
   */
  private DruidSqlSafetyFilterConfiguration() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Registers DruidSqlSafetyFilter with a DruidDataSource.
   *
   * <p>This method creates a new filter instance and adds it to the beginning of the datasource's
   * proxy filters list to ensure it executes before other filters like StatFilter.</p>
   *
   * @param dataSource the DruidDataSource to register the filter with
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @throws IllegalArgumentException if any parameter is null
   */
  public static void registerFilter(
      DruidDataSource dataSource,
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {

    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource cannot be null");
    }
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(validator, strategy);

    // Get existing filters or create new list
    List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
    if (filters == null) {
      filters = new ArrayList<>();
    } else {
      // Create mutable copy if needed
      filters = new ArrayList<>(filters);
    }

    // Add safety filter at the beginning to execute first
    filters.add(0, filter);
    dataSource.setProxyFilters(filters);
  }

  /**
   * Registers an existing DruidSqlSafetyFilter instance with a DruidDataSource.
   *
   * <p>This method is useful when you want to share a single filter instance across multiple
   * datasources or when using Spring dependency injection.</p>
   *
   * @param dataSource the DruidDataSource to register the filter with
   * @param filter the pre-configured filter instance
   * @throws IllegalArgumentException if any parameter is null
   */
  public static void registerFilter(DruidDataSource dataSource, DruidSqlSafetyFilter filter) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource cannot be null");
    }
    if (filter == null) {
      throw new IllegalArgumentException("filter cannot be null");
    }

    // Get existing filters or create new list
    List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
    if (filters == null) {
      filters = new ArrayList<>();
    } else {
      filters = new ArrayList<>(filters);
    }

    // Add safety filter at the beginning to execute first
    filters.add(0, filter);
    dataSource.setProxyFilters(filters);
  }

  /**
   * Creates a Spring BeanPostProcessor that automatically registers the filter with all
   * DruidDataSource beans.
   *
   * <p>This method requires Spring Framework on the classpath. The returned BeanPostProcessor
   * will intercept all DruidDataSource beans during initialization and register the safety
   * filter.</p>
   *
   * <p><strong>Note:</strong> This method uses reflection to avoid hard dependency on Spring.
   * If Spring is not available, the method will throw ClassNotFoundException.</p>
   *
   * @param filter the filter to register with all DruidDataSource beans
   * @return a Spring BeanPostProcessor instance
   * @throws IllegalArgumentException if filter is null
   * @throws RuntimeException if Spring Framework is not available
   */
  public static Object createSpringBeanPostProcessor(final DruidSqlSafetyFilter filter) {
    if (filter == null) {
      throw new IllegalArgumentException("filter cannot be null");
    }

    try {
      // Use reflection to avoid hard dependency on Spring
      Class<?> beanPostProcessorClass =
          Class.forName("org.springframework.beans.factory.config.BeanPostProcessor");

      return java.lang.reflect.Proxy.newProxyInstance(
          DruidSqlSafetyFilterConfiguration.class.getClassLoader(),
          new Class<?>[] {beanPostProcessorClass},
          (proxy, method, args) -> {
            if ("postProcessAfterInitialization".equals(method.getName())) {
              Object bean = args[0];
              if (bean instanceof DruidDataSource) {
                registerFilter((DruidDataSource) bean, filter);
              }
              return bean;
            }
            return args != null && args.length > 0 ? args[0] : null;
          });
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Spring Framework not found on classpath. "
              + "Add spring-beans dependency to use Spring integration.",
          e);
    }
  }

  /**
   * Removes all DruidSqlSafetyFilter instances from a DruidDataSource.
   *
   * <p>This method is useful for testing or dynamic filter management.</p>
   *
   * <p><strong>Implementation Note:</strong> Druid uses CopyOnWriteArrayList for proxy filters
   * and getProxyFilters() returns the same list instance. This method directly manipulates
   * the returned list by removing DruidSqlSafetyFilter instances using removeIf().</p>
   *
   * @param dataSource the DruidDataSource to remove filters from
   * @throws IllegalArgumentException if dataSource is null
   */
  public static void removeFilter(DruidDataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource cannot be null");
    }

    List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
    if (filters == null || filters.isEmpty()) {
      return;
    }

    // Druid returns a CopyOnWriteArrayList - we can directly manipulate it
    // Remove all DruidSqlSafetyFilter instances
    filters.removeIf(filter -> filter instanceof DruidSqlSafetyFilter);
  }
}

