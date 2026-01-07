package com.footstone.sqlguard.spring.autoconfigure.interceptor;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for Druid SQL Guard filter.
 *
 * <p>This configuration has <strong>second priority</strong> after MyBatis/MyBatis-Plus.
 * It only activates when no MyBatis interceptor is present.</p>
 *
 * <p>This configuration is automatically activated when:</p>
 * <ul>
 *   <li>Druid is on the classpath (DruidDataSource detected)</li>
 *   <li>No higher-priority interceptor (MyBatis) is enabled</li>
 *   <li>sql-guard.interceptors.druid.enabled=true (default)</li>
 * </ul>
 *
 * <p>It registers a BeanPostProcessor that automatically adds
 * {@link com.footstone.sqlguard.interceptor.jdbc.druid.DruidSqlSafetyFilter}
 * to all DruidDataSource beans.</p>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "com.alibaba.druid.pool.DruidDataSource")
@ConditionalOnMissingBean(SqlGuardInterceptorEnabled.class)
@ConditionalOnProperty(name = "sql-guard.interceptors.druid.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 100)
public class DruidInterceptorAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DruidInterceptorAutoConfiguration.class);

    /**
     * Creates the marker bean indicating Druid interceptor is enabled.
     * This prevents lower-priority interceptors (HikariCP, P6Spy) from loading.
     *
     * @return SqlGuardInterceptorEnabled marker
     */
    @Bean
    public SqlGuardInterceptorEnabled druidInterceptorMarker() {
        logger.info("Druid interceptor selected as primary SQL Guard interceptor");
        return () -> "druid";
    }

    /**
     * Creates a BeanPostProcessor that registers DruidSqlSafetyFilter with all DruidDataSource beans.
     *
     * @param validator the SQL safety validator
     * @param properties SQL Guard properties
     * @return BeanPostProcessor for Druid integration
     */
    @Bean
    public BeanPostProcessor druidSqlSafetyFilterPostProcessor(
            DefaultSqlSafetyValidator validator,
            SqlGuardProperties properties) {
        
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!isDruidDataSource(bean)) {
                    return bean;
                }

                try {
                    registerFilterWithDataSource(bean, beanName, validator, properties);
                } catch (Exception e) {
                    logger.error("Failed to register DruidSqlSafetyFilter with DruidDataSource bean [{}]", 
                                beanName, e);
                }
                
                return bean;
            }
        };
    }

    private boolean isDruidDataSource(Object bean) {
        try {
            Class<?> druidClass = Class.forName("com.alibaba.druid.pool.DruidDataSource");
            return druidClass.isInstance(bean);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void registerFilterWithDataSource(Object dataSource, String beanName,
                                               DefaultSqlSafetyValidator validator,
                                               SqlGuardProperties properties) throws Exception {
        
        // Check if sql-guard-jdbc-druid module is available
        Class<?> filterClass;
        Class<?> interceptorClass;
        Class<?> configClass;
        
        try {
            filterClass = Class.forName("com.footstone.sqlguard.interceptor.jdbc.druid.DruidSqlSafetyFilter");
            interceptorClass = Class.forName("com.footstone.sqlguard.interceptor.jdbc.druid.DruidJdbcInterceptor");
            configClass = Class.forName("com.footstone.sqlguard.interceptor.jdbc.druid.DruidInterceptorConfig");
        } catch (ClassNotFoundException e) {
            logger.debug("sql-guard-jdbc-druid module not on classpath, skipping Druid filter registration");
            return;
        }

        // Create default config
        Object config = createDefaultDruidConfig(configClass, properties);
        
        // Create interceptor with config and validator
        Object interceptor = interceptorClass
            .getConstructor(configClass, DefaultSqlSafetyValidator.class)
            .newInstance(config, validator);
        
        // Create filter with interceptor
        Object filter = filterClass
            .getConstructor(interceptorClass)
            .newInstance(interceptor);
        
        // Register filter with DruidDataSource using DruidSqlSafetyFilterConfiguration
        Class<?> configurationClass = Class.forName(
            "com.footstone.sqlguard.interceptor.jdbc.druid.DruidSqlSafetyFilterConfiguration");
        
        java.lang.reflect.Method registerMethod = configurationClass.getMethod(
            "registerFilter",
            Class.forName("com.alibaba.druid.pool.DruidDataSource"),
            filterClass);
        
        registerMethod.invoke(null, dataSource, filter);
        
        logger.info("Registered DruidSqlSafetyFilter with DruidDataSource bean [{}]", beanName);
    }

    /**
     * Creates a default DruidInterceptorConfig using dynamic proxy.
     */
    private Object createDefaultDruidConfig(Class<?> configClass, SqlGuardProperties properties) {
        return java.lang.reflect.Proxy.newProxyInstance(
            configClass.getClassLoader(),
            new Class<?>[] { configClass },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isEnabled":
                        return properties.getInterceptors().getJdbc().isEnabled();
                    case "getStrategy":
                        // Return ViolationStrategy based on properties
                        try {
                            Class<?> strategyClass = Class.forName(
                                "com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy");
                            return Enum.valueOf((Class<Enum>) strategyClass, 
                                properties.getActiveStrategy().toUpperCase());
                        } catch (Exception e) {
                            // Default to LOG
                            Class<?> strategyClass = Class.forName(
                                "com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy");
                            return Enum.valueOf((Class<Enum>) strategyClass, "LOG");
                        }
                    case "isAuditEnabled":
                        return false;
                    case "getExcludePatterns":
                        return java.util.Collections.emptyList();
                    case "getFilterPosition":
                        return 0;
                    case "isConnectionProxyEnabled":
                        return true;
                    case "toString":
                        return "DruidInterceptorConfig[proxy]";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return null;
                }
            });
    }
}
