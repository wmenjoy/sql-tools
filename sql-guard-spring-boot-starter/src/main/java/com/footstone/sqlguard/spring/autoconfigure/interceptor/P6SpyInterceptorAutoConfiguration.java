package com.footstone.sqlguard.spring.autoconfigure.interceptor;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;

/**
 * Auto-configuration for P6Spy SQL Guard event listener.
 *
 * <p>This configuration has the <strong>lowest priority</strong> among all interceptor types.
 * It only activates when no higher-priority interceptor (MyBatis, Druid, HikariCP) is present.</p>
 *
 * <p>This configuration is automatically activated when:</p>
 * <ul>
 *   <li>P6Spy is on the classpath (P6DataSource detected)</li>
 *   <li>No higher-priority interceptor is enabled</li>
 *   <li>sql-guard.interceptors.p6spy.enabled=true (default)</li>
 * </ul>
 *
 * <p>It creates a {@link com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SqlSafetyEventListener}
 * bean that integrates with P6Spy's event system.</p>
 *
 * <p><strong>Note:</strong> P6Spy integration requires additional configuration in spy.properties
 * to register the event listener. This auto-configuration provides the bean, but users must
 * configure P6Spy to use it.</p>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "com.p6spy.engine.spy.P6DataSource")
@ConditionalOnMissingBean(SqlGuardInterceptorEnabled.class)
@ConditionalOnProperty(name = "sql-guard.interceptors.p6spy.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 300)
public class P6SpyInterceptorAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(P6SpyInterceptorAutoConfiguration.class);

    /**
     * Creates the marker bean indicating P6Spy interceptor is enabled.
     *
     * @return SqlGuardInterceptorEnabled marker
     */
    @Bean
    public SqlGuardInterceptorEnabled p6spyInterceptorMarker() {
        logger.info("P6Spy interceptor selected as primary SQL Guard interceptor");
        return () -> "p6spy";
    }

    /**
     * Creates the P6Spy SQL Safety Event Listener bean.
     *
     * <p>This bean can be used with P6Spy's event system to intercept and validate SQL statements.
     * To enable, add to spy.properties:</p>
     * <pre>
     * modulelist=com.p6spy.engine.spy.P6SpyFactory
     * </pre>
     *
     * @param validator the SQL safety validator
     * @param properties SQL Guard properties
     * @return P6SqlSafetyEventListener bean, or null if module not available
     */
    @Bean(name = "p6SqlSafetyEventListener")
    public Object p6SqlSafetyEventListener(
            DefaultSqlSafetyValidator validator,
            SqlGuardProperties properties) {
        
        try {
            Class<?> listenerClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SqlSafetyEventListener");
            
            // Get violation strategy
            Class<?> strategyClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy");
            Object strategy;
            try {
                strategy = Enum.valueOf((Class<Enum>) strategyClass, 
                    properties.getActiveStrategy().toUpperCase());
            } catch (Exception e) {
                strategy = Enum.valueOf((Class<Enum>) strategyClass, "LOG");
            }
            
            // Create listener with validator and strategy
            Object listener = listenerClass
                .getConstructor(DefaultSqlSafetyValidator.class, strategyClass)
                .newInstance(validator, strategy);
            
            logger.info("Created P6SqlSafetyEventListener bean for P6Spy integration (strategy: {})", strategy);
            return listener;
            
        } catch (ClassNotFoundException e) {
            logger.debug("sql-guard-jdbc-p6spy module not on classpath, skipping P6SqlSafetyEventListener");
            return null;
        } catch (NoSuchMethodException e) {
            // Try simpler constructor
            try {
                Class<?> listenerClass = Class.forName(
                    "com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SqlSafetyEventListener");
                Object listener = listenerClass
                    .getConstructor(DefaultSqlSafetyValidator.class)
                    .newInstance(validator);
                
                logger.info("Created P6SqlSafetyEventListener bean for P6Spy integration");
                return listener;
            } catch (Exception ex) {
                logger.error("Failed to create P6SqlSafetyEventListener", ex);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to create P6SqlSafetyEventListener", e);
            return null;
        }
    }

    @PostConstruct
    public void logConfiguration() {
        logger.info("P6Spy SQL Guard integration enabled. " +
                   "Ensure P6Spy is properly configured in spy.properties.");
    }
}
