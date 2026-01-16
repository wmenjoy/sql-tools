package com.footstone.sqlguard.spring.autoconfigure;

import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.KafkaAuditWriter;
import com.footstone.sqlguard.audit.LogbackAuditWriter;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot auto-configuration for Audit Log Writer.
 *
 * <p>AuditLogAutoConfiguration provides automatic configuration for audit log writing
 * based on the sql-guard.audit properties. It supports two writer types:</p>
 * <ul>
 *   <li><strong>LOGBACK:</strong> Write audit events to log file via logback (default)</li>
 *   <li><strong>KAFKA:</strong> Write audit events directly to Kafka topic</li>
 * </ul>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * # Use Logback (default)
 * sql-guard:
 *   audit:
 *     enabled: true
 *     writer-type: LOGBACK
 *
 * # Use Kafka
 * sql-guard:
 *   audit:
 *     enabled: true
 *     writer-type: KAFKA
 *     kafka:
 *       topic: sql-audit-events
 * }</pre>
 *
 * <p><strong>Bean Creation Logic:</strong></p>
 * <ul>
 *   <li>If writer-type=LOGBACK: Creates LogbackAuditWriter bean</li>
 *   <li>If writer-type=KAFKA and KafkaTemplate available: Creates KafkaAuditWriter bean</li>
 *   <li>If audit is disabled: No AuditLogWriter bean is created</li>
 *   <li>Users can override by defining their own AuditLogWriter bean</li>
 * </ul>
 *
 * @see AuditLogWriter
 * @see LogbackAuditWriter
 * @see KafkaAuditWriter
 * @see SqlGuardProperties.AuditConfig
 * @since 2.0.0
 */
@Configuration
@EnableConfigurationProperties(SqlGuardProperties.class)
@ConditionalOnProperty(prefix = "sql-guard.audit", name = "enabled", havingValue = "true")
@AutoConfigureAfter(KafkaAutoConfiguration.class)
public class AuditLogAutoConfiguration {

    /**
     * Creates LogbackAuditWriter bean when writer-type is LOGBACK.
     *
     * <p>This is the default writer type. It writes audit events to log file
     * using a dedicated logback appender (defined in logback-audit.xml).</p>
     *
     * <p><strong>Performance:</strong> Asynchronous file I/O, minimal overhead</p>
     * <p><strong>Use case:</strong> Simple deployments, log aggregation via Filebeat/Fluentd</p>
     *
     * @return LogbackAuditWriter instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sql-guard.audit", name = "writer-type", havingValue = "LOGBACK", matchIfMissing = true)
    public AuditLogWriter logbackAuditWriter() {
        return new LogbackAuditWriter();
    }

    /**
     * Creates KafkaAuditWriter bean when writer-type is KAFKA and KafkaTemplate is available.
     *
     * <p>This writer sends audit events directly to Kafka topic for centralized
     * processing by the audit service.</p>
     *
     * <p><strong>Performance:</strong> Asynchronous Kafka send, fire-and-forget pattern</p>
     * <p><strong>Use case:</strong> Centralized audit processing, real-time analysis</p>
     *
     * <p><strong>Requirements:</strong></p>
     * <ul>
     *   <li>spring-kafka dependency must be on classpath</li>
     *   <li>Kafka bootstrap servers must be configured</li>
     *   <li>KafkaTemplate bean must be available</li>
     * </ul>
     *
     * @param kafkaTemplate Kafka template for sending messages
     * @param properties SQL Guard configuration properties
     * @return KafkaAuditWriter instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "sql-guard.audit", name = "writer-type", havingValue = "KAFKA")
    public AuditLogWriter kafkaAuditWriter(
            KafkaTemplate<String, String> kafkaTemplate,
            SqlGuardProperties properties) {
        String topic = properties.getAudit().getKafka().getTopic();
        return new KafkaAuditWriter(kafkaTemplate, topic);
    }
}
