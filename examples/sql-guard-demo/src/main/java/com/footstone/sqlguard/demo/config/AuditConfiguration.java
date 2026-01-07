package com.footstone.sqlguard.demo.config;

import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.LogbackAuditWriter;
import com.footstone.sqlguard.interceptor.mybatis.SqlAuditInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SQL Guard audit logging (validation disabled by default).
 *
 * <p>This configuration enables:</p>
 * <ul>
 *   <li>Post-execution audit logging via SqlAuditInterceptor</li>
 *   <li>SQL safety validation is DISABLED by default (all rules have enabled=false)</li>
 * </ul>
 *
 * <p>To enable validation rules, configure them explicitly in application.yml:</p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     no-where-clause:
 *       enabled: true
 *       risk-level: CRITICAL
 * }</pre>
 *
 * <p>Audit events are written to logs/audit/audit.log in JSON format matching the schema in
 * audit-log-schema.json.</p>
 *
 * <p><strong>Audit Event Format:</strong></p>
 * <pre>{@code
 * {
 *   "sqlId": "5d41402abc4b2a76b9719d911017c592",
 *   "sql": "SELECT * FROM user WHERE id = ?",
 *   "sqlType": "SELECT",
 *   "mapperId": "com.footstone.sqlguard.demo.mapper.UserMapper.selectById",
 *   "datasource": "primary",
 *   "executionTimeMs": 150,
 *   "rowsAffected": 1,
 *   "timestamp": "2025-12-29T10:30:45.123Z",
 *   "violations": null  // null when validation is disabled
 * }
 * }</pre>
 *
 * @see LogbackAuditWriter
 * @see SqlAuditInterceptor
 */
@Configuration
public class AuditConfiguration {

    /**
     * Creates LogbackAuditWriter bean for writing audit events to Logback.
     *
     * <p>This writer uses the dedicated logger "com.footstone.sqlguard.audit.AUDIT"
     * to write JSON audit events asynchronously.</p>
     *
     * @return LogbackAuditWriter instance
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogWriter auditLogWriter() {
        return new LogbackAuditWriter();
    }

    /**
     * BeanPostProcessor to register SqlAuditInterceptor after SqlSessionFactory creation.
     *
     * <p>This approach avoids circular dependency issues by registering the interceptor
     * AFTER the SqlSessionFactory bean has been created.</p>
     *
     * <p><strong>Note:</strong> SqlSafetyInterceptor is NOT registered by default.
     * All validation rules are disabled. Only audit logging is enabled.</p>
     *
     * <p>To enable validation rules, configure them explicitly in application.yml:</p>
     * <pre>{@code
     * sql-guard:
     *   rules:
     *     no-where-clause:
     *       enabled: true
     * }</pre>
     *
     * @param auditLogWriter audit log writer
     * @return BeanPostProcessor that registers the audit interceptor
     */
    @Bean
    public BeanPostProcessor sqlGuardInterceptorsRegistrar(AuditLogWriter auditLogWriter) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SqlSessionFactory) {
                    SqlSessionFactory sqlSessionFactory = (SqlSessionFactory) bean;

                    // SqlSafetyInterceptor registration removed - validation rules disabled by default
                    // Only audit logging is enabled. To enable validation rules, configure them in application.yml
                    // Example:
                    //   sql-guard:
                    //     rules:
                    //       no-where-clause:
                    //         enabled: true

                    // Register SqlAuditInterceptor (post-execution audit)
                    SqlAuditInterceptor auditInterceptor = new SqlAuditInterceptor(auditLogWriter);
                    sqlSessionFactory.getConfiguration().addInterceptor(auditInterceptor);
                }
                return bean;
            }
        };
    }
}
