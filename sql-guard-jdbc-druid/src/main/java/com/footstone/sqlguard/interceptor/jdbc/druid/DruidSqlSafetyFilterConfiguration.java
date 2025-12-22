package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration helper for registering DruidSqlSafetyFilter with DruidDataSource.
 *
 * <p>This class provides utility methods for programmatic filter registration and
 * Spring Boot integration. The filter is registered at the beginning of the filter
 * list to ensure it executes before other Druid filters.</p>
 *
 * <h2>Filter Ordering Strategy</h2>
 * <p>Druid executes filters in the order they appear in the proxy filters list:</p>
 * <ul>
 *   <li>Position 0: SQL Guard Safety Filter (pre-execution validation)</li>
 *   <li>Position 1+: Druid built-in filters (StatFilter, WallFilter, etc.)</li>
 *   <li>Last position: SQL Guard Audit Filter (post-execution audit)</li>
 * </ul>
 *
 * <h2>Usage Example (Programmatic)</h2>
 * <pre>{@code
 * DruidDataSource dataSource = new DruidDataSource();
 * dataSource.setUrl("jdbc:mysql://localhost:3306/mydb");
 *
 * DruidInterceptorConfig config = new MyDruidConfig();
 * DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(rulesConfig);
 *
 * DruidSqlSafetyFilterConfiguration.registerFilter(dataSource, config, validator);
 * }</pre>
 *
 * <h2>Usage Example (Spring Boot)</h2>
 * <pre>{@code
 * @Configuration
 * @ConditionalOnClass(DruidDataSource.class)
 * @EnableConfigurationProperties(DruidSqlGuardProperties.class)
 * public class DruidAutoConfiguration {
 *
 *     @Bean
 *     @ConditionalOnMissingBean
 *     public DruidSqlSafetyFilter druidSqlSafetyFilter(
 *             DruidSqlGuardProperties properties,
 *             DefaultSqlSafetyValidator validator) {
 *         DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(properties, validator);
 *         return new DruidSqlSafetyFilter(interceptor);
 *     }
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see DruidSqlSafetyFilter
 * @see DruidJdbcInterceptor
 * @see DruidInterceptorConfig
 */
public class DruidSqlSafetyFilterConfiguration {

    /**
     * Private constructor to prevent instantiation.
     */
    private DruidSqlSafetyFilterConfiguration() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers DruidSqlSafetyFilter with a DruidDataSource using configuration and validator.
     *
     * <p>This method creates a new interceptor and filter, adding them to the beginning
     * of the datasource's proxy filters list to ensure execution before other filters.</p>
     *
     * @param dataSource the DruidDataSource to register the filter with
     * @param config the Druid interceptor configuration
     * @param validator the SQL safety validator
     * @throws IllegalArgumentException if any parameter is null
     */
    public static void registerFilter(
            DruidDataSource dataSource,
            DruidInterceptorConfig config,
            DefaultSqlSafetyValidator validator) {
        
        validateNotNull(dataSource, "dataSource");
        validateNotNull(config, "config");
        validateNotNull(validator, "validator");

        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);

        addFilterToDataSource(dataSource, filter, 0);
    }

    /**
     * Registers an existing DruidSqlSafetyFilter instance with a DruidDataSource.
     *
     * <p>This method is useful when you want to share a single filter instance
     * across multiple datasources or when using Spring dependency injection.</p>
     *
     * @param dataSource the DruidDataSource to register the filter with
     * @param filter the pre-configured filter instance
     * @throws IllegalArgumentException if any parameter is null
     */
    public static void registerFilter(
            DruidDataSource dataSource,
            DruidSqlSafetyFilter filter) {
        
        validateNotNull(dataSource, "dataSource");
        validateNotNull(filter, "filter");

        addFilterToDataSource(dataSource, filter, 0);
    }

    /**
     * Registers DruidSqlSafetyFilter at a specific position in the filter chain.
     *
     * @param dataSource the DruidDataSource to register the filter with
     * @param filter the pre-configured filter instance
     * @param position the position in the filter list (0 = first, -1 = last)
     * @throws IllegalArgumentException if dataSource or filter is null
     */
    public static void registerFilterAtPosition(
            DruidDataSource dataSource,
            DruidSqlSafetyFilter filter,
            int position) {
        
        validateNotNull(dataSource, "dataSource");
        validateNotNull(filter, "filter");

        addFilterToDataSource(dataSource, filter, position);
    }

    /**
     * Creates a Spring BeanPostProcessor that automatically registers the filter
     * with all DruidDataSource beans.
     *
     * <p>This method uses reflection to avoid hard dependency on Spring Framework.
     * If Spring is not available, the method will throw RuntimeException.</p>
     *
     * @param filter the filter to register with all DruidDataSource beans
     * @return a Spring BeanPostProcessor instance
     * @throws IllegalArgumentException if filter is null
     * @throws RuntimeException if Spring Framework is not available
     */
    public static Object createSpringBeanPostProcessor(final DruidSqlSafetyFilter filter) {
        validateNotNull(filter, "filter");

        try {
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
                    "Spring Framework not found on classpath. " +
                            "Add spring-beans dependency to use Spring integration.",
                    e);
        }
    }

    /**
     * Removes all DruidSqlSafetyFilter instances from a DruidDataSource.
     *
     * <p>This method is useful for testing or dynamic filter management.</p>
     *
     * @param dataSource the DruidDataSource to remove filters from
     * @throws IllegalArgumentException if dataSource is null
     */
    public static void removeFilter(DruidDataSource dataSource) {
        validateNotNull(dataSource, "dataSource");

        List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
        if (filters == null || filters.isEmpty()) {
            return;
        }

        filters.removeIf(filter -> filter instanceof DruidSqlSafetyFilter);
    }

    /**
     * Removes all SQL Guard filters (Safety and Audit) from a DruidDataSource.
     *
     * @param dataSource the DruidDataSource to remove filters from
     * @throws IllegalArgumentException if dataSource is null
     */
    public static void removeAllFilters(DruidDataSource dataSource) {
        validateNotNull(dataSource, "dataSource");

        List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
        if (filters == null || filters.isEmpty()) {
            return;
        }

        filters.removeIf(filter ->
                filter instanceof DruidSqlSafetyFilter ||
                        filter instanceof DruidSqlAuditFilter);
    }

    /**
     * Creates a default DruidInterceptorConfig with specified strategy.
     *
     * <p>Utility method for creating a simple configuration for testing or
     * quick setup scenarios.</p>
     *
     * @param strategy the violation handling strategy
     * @return a default configuration implementation
     */
    public static DruidInterceptorConfig createDefaultConfig(ViolationStrategy strategy) {
        return new DruidInterceptorConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public ViolationStrategy getStrategy() {
                return strategy;
            }

            @Override
            public boolean isAuditEnabled() {
                return false;
            }

            @Override
            public List<String> getExcludePatterns() {
                return Collections.emptyList();
            }

            @Override
            public int getFilterPosition() {
                return 0;
            }

            @Override
            public boolean isConnectionProxyEnabled() {
                return true;
            }
        };
    }

    // ========== Helper Methods ==========

    /**
     * Adds a filter to the datasource at the specified position.
     *
     * @param dataSource the datasource
     * @param filter the filter to add
     * @param position the position (0 = first, -1 = last)
     */
    private static void addFilterToDataSource(
            DruidDataSource dataSource,
            com.alibaba.druid.filter.Filter filter,
            int position) {
        
        List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        } else {
            filters = new ArrayList<>(filters);
        }

        if (position < 0 || position > filters.size()) {
            filters.add(filter);
        } else {
            filters.add(position, filter);
        }
        
        dataSource.setProxyFilters(filters);
    }

    /**
     * Validates that an object is not null.
     *
     * @param obj the object to validate
     * @param name the parameter name for error message
     * @throws IllegalArgumentException if obj is null
     */
    private static void validateNotNull(Object obj, String name) {
        if (obj == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}

