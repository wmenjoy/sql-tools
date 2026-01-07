package com.footstone.sqlguard.spring.autoconfigure.interceptor;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for MyBatis-Plus SQL Guard inner interceptor.
 *
 * <p>This configuration has the <strong>highest priority</strong> (same as MyBatis).
 * MyBatis and MyBatis-Plus can coexist as they work at different levels.</p>
 *
 * <p>This configuration is automatically activated when:</p>
 * <ul>
 *   <li>MyBatis-Plus is on the classpath (MybatisPlusInterceptor detected)</li>
 *   <li>sql-guard.interceptors.mybatis-plus.enabled=true (default)</li>
 * </ul>
 *
 * <p>It provides a {@link com.footstone.sqlguard.interceptor.mp.MpSqlSafetyInnerInterceptor}
 * bean that can be added to MybatisPlusInterceptor configuration.</p>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
@ConditionalOnProperty(name = "sql-guard.interceptors.mybatis-plus.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class MyBatisPlusInterceptorAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisPlusInterceptorAutoConfiguration.class);

    /**
     * Creates the MyBatis-Plus SQL Safety InnerInterceptor bean.
     *
     * <p>This bean can be injected into your MybatisPlusInterceptor configuration:</p>
     * <pre>{@code
     * @Bean
     * public MybatisPlusInterceptor mybatisPlusInterceptor(
     *     @Autowired(required = false) InnerInterceptor sqlSafetyInnerInterceptor) {
     *     MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
     *     if (sqlSafetyInnerInterceptor != null) {
     *         interceptor.addInnerInterceptor(sqlSafetyInnerInterceptor);
     *     }
     *     return interceptor;
     * }
     * }</pre>
     *
     * @param validator the SQL safety validator
     * @param properties SQL Guard properties
     * @return MpSqlSafetyInnerInterceptor bean, or null if module not available
     */
    @Bean(name = "sqlSafetyInnerInterceptor")
    public Object mpSqlSafetyInnerInterceptor(
            DefaultSqlSafetyValidator validator,
            SqlGuardProperties properties) {
        
        try {
            Class<?> interceptorClass = Class.forName(
                "com.footstone.sqlguard.interceptor.mp.MpSqlSafetyInnerInterceptor");
            
            Object interceptor = interceptorClass
                .getConstructor(DefaultSqlSafetyValidator.class)
                .newInstance(validator);
            
            logger.info("Created MpSqlSafetyInnerInterceptor bean for MyBatis-Plus integration");
            return interceptor;
            
        } catch (ClassNotFoundException e) {
            logger.debug("sql-guard-mp module not on classpath, skipping MpSqlSafetyInnerInterceptor");
            return null;
        } catch (Exception e) {
            logger.error("Failed to create MpSqlSafetyInnerInterceptor", e);
            return null;
        }
    }
}
