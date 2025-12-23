package com.footstone.audit.service.core.storage.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.util.Properties;

/**
 * Storage configuration for audit service.
 *
 * <h2>Flexible Storage Architecture</h2>
 * <p>The audit service uses a two-tier storage model:</p>
 * <ul>
 *   <li><b>Metadata Storage</b>: Audit reports, checker configs (relational DB)</li>
 *   <li><b>Time-Series Storage</b>: Execution logs, high-volume events (TSDB/Search engine)</li>
 * </ul>
 *
 * <h2>Supported Combinations</h2>
 * <table>
 *   <tr><th>Mode</th><th>Metadata</th><th>Time-Series</th><th>Best For</th></tr>
 *   <tr><td>mysql-es</td><td>MySQL</td><td>Elasticsearch</td><td>Most companies (recommended)</td></tr>
 *   <tr><td>mysql-only</td><td>MySQL</td><td>MySQL</td><td>Simple deployment</td></tr>
 *   <tr><td>full</td><td>PostgreSQL</td><td>ClickHouse</td><td>Large-scale analytics</td></tr>
 *   <tr><td>postgresql-only</td><td>PostgreSQL</td><td>PostgreSQL</td><td>Mid-scale</td></tr>
 *   <tr><td>elasticsearch</td><td>Elasticsearch</td><td>Elasticsearch</td><td>Search-focused</td></tr>
 * </table>
 *
 * <h2>Configuration Example (Recommended: MySQL + Elasticsearch)</h2>
 * <pre>
 * audit:
 *   storage:
 *     mode: mysql-es  # MySQL for metadata, Elasticsearch for logs
 *     elasticsearch:
 *       hosts: localhost:9200
 *       username: elastic
 *       password: changeme
 *
 * spring:
 *   datasource:
 *     url: jdbc:mysql://localhost:3306/audit
 *     username: audit_user
 *     password: secure_password
 * </pre>
 *
 * <h2>Alternative: ClickHouse Mode (Large Scale)</h2>
 * <pre>
 * audit:
 *   storage:
 *     mode: full  # PostgreSQL + ClickHouse
 *     clickhouse:
 *       url: jdbc:clickhouse://localhost:8123/audit
 * </pre>
 *
 * @since 2.0.0
 */
@Configuration
@Slf4j
public class StorageConfig {

    /**
     * ClickHouse DataSource for modes requiring ClickHouse.
     * Used for high-throughput time-series audit data (optional, for large-scale deployments).
     */
    @Bean
    @ConditionalOnProperty(name = "audit.storage.mode", havingValue = "full")
    public ClickHouseDataSource clickHouseDataSource(
            @Value("${audit.storage.clickhouse.url}") String url,
            @Value("${audit.storage.clickhouse.username:default}") String username,
            @Value("${audit.storage.clickhouse.password:}") String password
    ) throws SQLException {
        log.info("Initializing ClickHouse DataSource for full storage mode (large-scale analytics)");
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return new ClickHouseDataSource(url, properties);
    }
}
