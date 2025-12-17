package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration helper for integrating SQL safety validation with HikariCP.
 *
 * <p>HikariSqlSafetyConfiguration provides utility methods to wrap HikariDataSource
 * with SQL safety validation proxies. Since HikariCP doesn't expose a ProxyFactory
 * interface, this configuration wraps the DataSource itself.</p>
 *
 * <p><strong>Usage Patterns:</strong></p>
 * <ul>
 *   <li><strong>Programmatic Configuration</strong> - Wrap existing HikariDataSource</li>
 *   <li><strong>Spring Boot Integration</strong> - Use BeanPostProcessor to auto-wrap</li>
 *   <li><strong>Manual Configuration</strong> - Create wrapped DataSource from HikariConfig</li>
 * </ul>
 *
 * <p><strong>Example - Programmatic Configuration:</strong></p>
 * <pre>{@code
 * HikariConfig config = new HikariConfig();
 * config.setJdbcUrl("jdbc:mysql://localhost:3306/db");
 * HikariDataSource hikariDs = new HikariDataSource(config);
 *
 * // Wrap with SQL safety
 * DataSource safeDs = HikariSqlSafetyConfiguration.wrapDataSource(
 *     hikariDs,
 *     validator,
 *     ViolationStrategy.BLOCK
 * );
 * }</pre>
 *
 * <p><strong>Example - Spring Boot Auto-Configuration:</strong></p>
 * <pre>{@code
 * @Configuration
 * public class DataSourceConfig {
 *
 *     @Bean
 *     public BeanPostProcessor hikariSqlSafetyPostProcessor(
 *         DefaultSqlSafetyValidator validator) {
 *         return HikariSqlSafetyConfiguration.createBeanPostProcessor(
 *             validator,
 *             ViolationStrategy.WARN
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Leak Detection Compatibility:</strong></p>
 * <p>The SQL safety proxy is transparent to HikariCP's leak detection mechanism.
 * Connection leak detection will still work correctly as the proxy delegates all
 * connection lifecycle methods to the underlying HikariCP connection.</p>
 *
 * @see HikariSqlSafetyProxyFactory
 * @see DefaultSqlSafetyValidator
 * @see ViolationStrategy
 */
public class HikariSqlSafetyConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(HikariSqlSafetyConfiguration.class);

  /**
   * Wraps a HikariDataSource with SQL safety validation.
   *
   * <p>This is the primary method for adding SQL safety to an existing HikariDataSource.
   * The wrapped DataSource will validate all SQL statements before execution according
   * to the configured violation strategy.</p>
   *
   * @param hikariDataSource the HikariDataSource to wrap
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @return wrapped DataSource with SQL safety validation
   * @throws IllegalArgumentException if any parameter is null
   */
  public static DataSource wrapDataSource(
      HikariDataSource hikariDataSource,
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {
    if (hikariDataSource == null) {
      throw new IllegalArgumentException("hikariDataSource cannot be null");
    }
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    logger.info("Wrapping HikariDataSource [{}] with SQL safety validation (strategy: {})",
        hikariDataSource.getPoolName(), strategy);

    return HikariSqlSafetyProxyFactory.wrap(hikariDataSource, validator, strategy);
  }

  /**
   * Creates a wrapped HikariDataSource from HikariConfig with SQL safety validation.
   *
   * <p>This method creates a new HikariDataSource from the provided configuration
   * and immediately wraps it with SQL safety validation. This is useful when you
   * want to configure HikariCP and SQL safety in one step.</p>
   *
   * @param config the HikariCP configuration
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @return wrapped DataSource with SQL safety validation
   * @throws IllegalArgumentException if any parameter is null
   */
  public static DataSource createSafeDataSource(
      HikariConfig config,
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {
    if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    logger.info("Creating HikariDataSource with SQL safety validation (strategy: {})", strategy);

    HikariDataSource hikariDataSource = new HikariDataSource(config);
    return wrapDataSource(hikariDataSource, validator, strategy);
  }

  /**
   * Creates a Spring BeanPostProcessor that automatically wraps HikariDataSource beans.
   *
   * <p>This BeanPostProcessor intercepts HikariDataSource bean creation and wraps them
   * with SQL safety validation. This enables automatic SQL safety for all HikariCP
   * datasources in a Spring application context.</p>
   *
   * <p><strong>Usage in Spring Boot:</strong></p>
   * <pre>{@code
   * @Configuration
   * public class DataSourceConfig {
   *
   *     @Bean
   *     public BeanPostProcessor hikariSqlSafetyPostProcessor(
   *         DefaultSqlSafetyValidator validator) {
   *         return HikariSqlSafetyConfiguration.createBeanPostProcessor(
   *             validator,
   *             ViolationStrategy.WARN
   *         );
   *     }
   * }
   * }</pre>
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @return BeanPostProcessor that wraps HikariDataSource beans
   * @throws IllegalArgumentException if any parameter is null
   */
  public static org.springframework.beans.factory.config.BeanPostProcessor createBeanPostProcessor(
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    return new org.springframework.beans.factory.config.BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource) {
          HikariDataSource hikariDs = (HikariDataSource) bean;
          logger.info("Auto-wrapping HikariDataSource bean [{}] with SQL safety validation",
              beanName);
          return wrapDataSource(hikariDs, validator, strategy);
        }
        return bean;
      }
    };
  }

  /**
   * Validates that HikariCP leak detection is compatible with SQL safety proxy.
   *
   * <p>This method checks that the wrapped DataSource preserves HikariCP's leak
   * detection functionality. It's primarily used for testing and validation.</p>
   *
   * @param wrappedDataSource the wrapped DataSource to validate
   * @return true if leak detection is compatible, false otherwise
   */
  public static boolean isLeakDetectionCompatible(DataSource wrappedDataSource) {
    if (wrappedDataSource == null) {
      return false;
    }

    try {
      // Check if we can get a connection and it's properly proxied
      java.sql.Connection conn = wrappedDataSource.getConnection();
      boolean isProxy = java.lang.reflect.Proxy.isProxyClass(conn.getClass());
      conn.close();
      return isProxy;
    } catch (Exception e) {
      logger.error("Leak detection compatibility check failed", e);
      return false;
    }
  }
}



