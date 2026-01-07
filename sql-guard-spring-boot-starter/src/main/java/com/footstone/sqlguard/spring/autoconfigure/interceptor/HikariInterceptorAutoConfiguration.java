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

import javax.sql.DataSource;

/**
 * Auto-configuration for HikariCP SQL Guard proxy.
 *
 * <p>This configuration has <strong>third priority</strong> after MyBatis and Druid.
 * It only activates when no higher-priority interceptor is present.</p>
 *
 * <p>This configuration is automatically activated when:</p>
 * <ul>
 *   <li>HikariCP is on the classpath (HikariDataSource detected)</li>
 *   <li>No higher-priority interceptor (MyBatis, Druid) is enabled</li>
 *   <li>sql-guard.interceptors.hikari.enabled=true (default)</li>
 * </ul>
 *
 * <p>It registers a BeanPostProcessor that automatically wraps
 * HikariDataSource beans with SQL safety validation proxies.</p>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
@ConditionalOnMissingBean(SqlGuardInterceptorEnabled.class)
@ConditionalOnProperty(name = "sql-guard.interceptors.hikari.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 200)
public class HikariInterceptorAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HikariInterceptorAutoConfiguration.class);

    /**
     * Creates the marker bean indicating HikariCP interceptor is enabled.
     * This prevents lower-priority interceptors (P6Spy) from loading.
     *
     * @return SqlGuardInterceptorEnabled marker
     */
    @Bean
    public SqlGuardInterceptorEnabled hikariInterceptorMarker() {
        logger.info("HikariCP interceptor selected as primary SQL Guard interceptor");
        return () -> "hikari";
    }

    /**
     * Creates a BeanPostProcessor that wraps HikariDataSource beans with SQL safety proxies.
     *
     * @param validator the SQL safety validator
     * @param properties SQL Guard properties
     * @return BeanPostProcessor for HikariCP integration
     */
    @Bean
    public BeanPostProcessor hikariSqlSafetyProxyPostProcessor(
            DefaultSqlSafetyValidator validator,
            SqlGuardProperties properties) {
        
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!isHikariDataSource(bean)) {
                    return bean;
                }

                try {
                    return wrapWithSafetyProxy(bean, beanName, validator, properties);
                } catch (Exception e) {
                    logger.error("Failed to wrap HikariDataSource bean [{}] with SQL safety proxy", 
                                beanName, e);
                    return bean;
                }
            }
        };
    }

    private boolean isHikariDataSource(Object bean) {
        try {
            Class<?> hikariClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            return hikariClass.isInstance(bean);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Object wrapWithSafetyProxy(Object dataSource, String beanName,
                                        DefaultSqlSafetyValidator validator,
                                        SqlGuardProperties properties) throws Exception {
        
        // Check if sql-guard-jdbc-hikari module is available
        Class<?> proxyFactoryClass;
        Class<?> strategyClass;
        
        try {
            proxyFactoryClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.hikari.HikariSqlSafetyProxyFactory");
            strategyClass = Class.forName(
                "com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy");
        } catch (ClassNotFoundException e) {
            logger.debug("sql-guard-jdbc-hikari module not on classpath, skipping HikariCP proxy wrapping");
            return dataSource;
        }

        // Check if already wrapped
        java.lang.reflect.Method isWrappedMethod = proxyFactoryClass.getMethod("isWrapped", DataSource.class);
        boolean alreadyWrapped = (Boolean) isWrappedMethod.invoke(null, dataSource);
        
        if (alreadyWrapped) {
            logger.debug("HikariDataSource bean [{}] is already wrapped, skipping", beanName);
            return dataSource;
        }

        // Get violation strategy from properties
        Object strategy;
        try {
            strategy = Enum.valueOf((Class<Enum>) strategyClass, 
                properties.getActiveStrategy().toUpperCase());
        } catch (Exception e) {
            // Default to LOG
            strategy = Enum.valueOf((Class<Enum>) strategyClass, "LOG");
        }

        // Wrap the datasource using HikariSqlSafetyProxyFactory.wrap()
        java.lang.reflect.Method wrapMethod = proxyFactoryClass.getMethod(
            "wrap",
            Class.forName("com.zaxxer.hikari.HikariDataSource"),
            DefaultSqlSafetyValidator.class,
            strategyClass);
        
        Object wrappedDataSource = wrapMethod.invoke(null, dataSource, validator, strategy);
        
        logger.info("Wrapped HikariDataSource bean [{}] with SQL safety proxy (strategy: {})", 
                   beanName, strategy);
        
        return wrappedDataSource;
    }
}
