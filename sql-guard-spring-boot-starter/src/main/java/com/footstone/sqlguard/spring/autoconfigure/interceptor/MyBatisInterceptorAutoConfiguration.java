package com.footstone.sqlguard.spring.autoconfigure.interceptor;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Auto-configuration for MyBatis SQL Guard interceptor.
 *
 * <p>This configuration has the <strong>highest priority</strong> among all interceptor types.
 * When MyBatis is on the classpath, this interceptor will be used instead of Druid/HikariCP/P6Spy.</p>
 *
 * <p>This configuration is automatically activated when:</p>
 * <ul>
 *   <li>MyBatis is on the classpath (SqlSessionFactory detected)</li>
 *   <li>sql-guard.interceptors.mybatis.enabled=true (default)</li>
 * </ul>
 *
 * <p>It registers {@link com.footstone.sqlguard.interceptor.mybatis.SqlSafetyInterceptor}
 * with all SqlSessionFactory beans in the application context.</p>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "org.apache.ibatis.session.SqlSessionFactory")
@ConditionalOnProperty(name = "sql-guard.interceptors.mybatis.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class MyBatisInterceptorAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisInterceptorAutoConfiguration.class);

    @Autowired(required = false)
    private List<SqlSessionFactory> sqlSessionFactories;

    @Autowired
    private DefaultSqlSafetyValidator validator;

    @Autowired
    private SqlGuardProperties properties;

    /**
     * Creates the marker bean indicating MyBatis interceptor is enabled.
     * This prevents lower-priority interceptors (Druid, HikariCP, P6Spy) from loading.
     *
     * @return SqlGuardInterceptorEnabled marker
     */
    @Bean
    public SqlGuardInterceptorEnabled mybatisInterceptorMarker() {
        logger.info("MyBatis interceptor selected as primary SQL Guard interceptor");
        return () -> "mybatis";
    }

    /**
     * Registers SQL Guard interceptor with all SqlSessionFactory beans.
     */
    @PostConstruct
    public void registerInterceptor() {
        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            logger.debug("No SqlSessionFactory beans found, skipping MyBatis interceptor registration");
            return;
        }

        try {
            // Dynamically load the interceptor class
            Class<?> interceptorClass = Class.forName(
                "com.footstone.sqlguard.interceptor.mybatis.SqlSafetyInterceptor");
            
            for (SqlSessionFactory factory : sqlSessionFactories) {
                // Check if interceptor is already registered
                boolean alreadyRegistered = factory.getConfiguration().getInterceptors().stream()
                    .anyMatch(i -> i.getClass().getName().equals(interceptorClass.getName()));
                
                if (!alreadyRegistered) {
                    // Create interceptor instance with validator
                    Object interceptor = interceptorClass
                        .getConstructor(DefaultSqlSafetyValidator.class)
                        .newInstance(validator);
                    
                    factory.getConfiguration().addInterceptor(
                        (org.apache.ibatis.plugin.Interceptor) interceptor);
                    
                    logger.info("Registered SqlSafetyInterceptor with SqlSessionFactory");
                } else {
                    logger.debug("SqlSafetyInterceptor already registered with SqlSessionFactory");
                }
            }
        } catch (ClassNotFoundException e) {
            logger.debug("sql-guard-mybatis module not on classpath, skipping interceptor registration");
        } catch (Exception e) {
            logger.error("Failed to register MyBatis SQL Guard interceptor", e);
        }
    }
}
