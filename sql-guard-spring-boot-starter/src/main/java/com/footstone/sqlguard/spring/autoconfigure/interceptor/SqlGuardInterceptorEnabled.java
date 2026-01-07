package com.footstone.sqlguard.spring.autoconfigure.interceptor;

/**
 * Marker interface indicating that a SQL Guard interceptor has been enabled.
 *
 * <p>This interface is used to implement a priority-based interceptor selection mechanism.
 * When multiple interceptor types are available on the classpath (e.g., MyBatis, Druid, HikariCP),
 * only one interceptor is activated based on the following priority order:</p>
 *
 * <ol>
 *   <li><strong>MyBatis/MyBatis-Plus</strong> - Highest priority (ORM-level interception)</li>
 *   <li><strong>Druid</strong> - Connection pool filter</li>
 *   <li><strong>HikariCP</strong> - Connection pool proxy</li>
 *   <li><strong>P6Spy</strong> - JDBC spy (lowest priority)</li>
 * </ol>
 *
 * <p>Each interceptor auto-configuration class creates a bean implementing this interface.
 * Lower-priority configurations use {@code @ConditionalOnMissingBean(SqlGuardInterceptorEnabled.class)}
 * to skip loading if a higher-priority interceptor is already enabled.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // High-priority configuration (MyBatis)
 * @Configuration
 * @AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
 * public class MyBatisInterceptorAutoConfiguration {
 *     @Bean
 *     public SqlGuardInterceptorEnabled mybatisInterceptorMarker() {
 *         return () -> "mybatis";
 *     }
 * }
 *
 * // Lower-priority configuration (Druid)
 * @Configuration
 * @ConditionalOnMissingBean(SqlGuardInterceptorEnabled.class)
 * @AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 100)
 * public class DruidInterceptorAutoConfiguration {
 *     @Bean
 *     public SqlGuardInterceptorEnabled druidInterceptorMarker() {
 *         return () -> "druid";
 *     }
 * }
 * }</pre>
 *
 * @since 2.0.0
 */
@FunctionalInterface
public interface SqlGuardInterceptorEnabled {

    /**
     * Returns the type of interceptor that is enabled.
     *
     * @return interceptor type name (e.g., "mybatis", "druid", "hikari", "p6spy")
     */
    String getInterceptorType();
}
