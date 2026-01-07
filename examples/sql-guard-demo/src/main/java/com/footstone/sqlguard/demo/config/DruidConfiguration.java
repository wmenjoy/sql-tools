package com.footstone.sqlguard.demo.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.interceptor.jdbc.druid.DruidSqlAuditFilter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Druid JDBC layer audit filter.
 *
 * <p>This configuration only registers DruidSqlAuditFilter for post-execution audit.
 * The SQL safety validation is handled by sql-guard-spring-boot-starter's auto-configuration
 * which uses priority-based interceptor selection:</p>
 *
 * <ol>
 *   <li><strong>MyBatis</strong> - Highest priority (ORM-level interception)</li>
 *   <li><strong>Druid</strong> - Second priority (connection pool filter)</li>
 *   <li><strong>HikariCP</strong> - Third priority (connection pool proxy)</li>
 *   <li><strong>P6Spy</strong> - Lowest priority (JDBC spy)</li>
 * </ol>
 *
 * <p>Since this demo uses MyBatis, the MyBatis interceptor will be automatically selected
 * for SQL safety validation. The Druid filter is only used for audit logging here.</p>
 */
@Configuration
@ConditionalOnClass(DruidDataSource.class)
public class DruidConfiguration {

    /**
     * BeanPostProcessor to register Druid audit filter with all DruidDataSource beans.
     *
     * <p>This only registers the audit filter. SQL safety validation is handled by
     * the auto-configured MyBatis interceptor (highest priority).</p>
     *
     * @param auditLogWriter audit log writer
     * @return BeanPostProcessor that registers audit filter
     */
    @Bean
    public BeanPostProcessor druidAuditFilterRegistrar(AuditLogWriter auditLogWriter) {

        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DruidDataSource) {
                    DruidDataSource dataSource = (DruidDataSource) bean;

                    // Get existing filters or create new list
                    List<com.alibaba.druid.filter.Filter> filters = dataSource.getProxyFilters();
                    if (filters == null) {
                        filters = new ArrayList<>();
                    } else {
                        filters = new ArrayList<>(filters);
                    }

                    // Register DruidSqlAuditFilter for post-execution audit logging
                    // SQL safety validation is handled by MyBatis interceptor (auto-configured)
                    DruidSqlAuditFilter auditFilter = new DruidSqlAuditFilter(auditLogWriter);
                    filters.add(auditFilter);

                    // Update datasource filters
                    dataSource.setProxyFilters(filters);
                }
                return bean;
            }
        };
    }
}
