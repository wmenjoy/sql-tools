package com.footstone.audit.service.core.storage.adapter;

import com.footstone.audit.service.core.storage.elasticsearch.ElasticsearchClientWrapper;
import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Execution log storage adapter for MySQL + Elasticsearch mode.
 *
 * <h2>Purpose</h2>
 * <p>In the mysql-es mode, this adapter stores execution logs in Elasticsearch
 * for time-series queries and full-text search, while MySQL stores metadata
 * (audit reports, checker configs) via JPA repositories.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                    mysql-es Mode                         │
 * ├─────────────────────────────────────────────────────────┤
 * │  Metadata Storage (MySQL)          │  Log Storage (ES)  │
 * │  - audit_reports_mysql             │  - sql-audit-*     │
 * │  - checker_config                  │  - Time-series     │
 * │  - JPA repositories                │  - Full-text search│
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Time-based index pattern: sql-audit-YYYY.MM.dd</li>
 *   <li>Bulk indexing for high throughput</li>
 *   <li>Full-text search on SQL statements</li>
 *   <li>Kibana integration ready</li>
 * </ul>
 *
 * @see ElasticsearchClientWrapper
 * @since 2.0.0
 */
@Component
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "mysql-es", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MySQLElasticsearchStorageAdapter implements ExecutionLogRepository {

    private final ElasticsearchClientWrapper esClient;
    
    private static final String INDEX_PREFIX = "sql-audit-";
    private static final DateTimeFormatter INDEX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Override
    public void log(AuditEvent event) {
        try {
            String indexName = getIndexName(event.getTimestamp());
            Map<String, Object> document = toDocument(event);
            
            esClient.index(indexName, document);
            log.debug("Indexed audit event to Elasticsearch: {}", indexName);
            
        } catch (Exception e) {
            log.error("Failed to log audit event to Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch write failed", e);
        }
    }

    @Override
    public void logBatch(List<AuditEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        
        try {
            // Group by index name for efficient bulk requests
            Map<String, List<Map<String, Object>>> byIndex = events.stream()
                    .collect(Collectors.groupingBy(
                            e -> getIndexName(e.getTimestamp()),
                            Collectors.mapping(this::toDocument, Collectors.toList())
                    ));
            
            for (Map.Entry<String, List<Map<String, Object>>> entry : byIndex.entrySet()) {
                esClient.bulkIndex(entry.getKey(), entry.getValue());
            }
            
            log.debug("Bulk indexed {} audit events to Elasticsearch", events.size());
            
        } catch (Exception e) {
            log.error("Failed to bulk log audit events to Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch bulk write failed", e);
        }
    }

    @Override
    public List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime) {
        try {
            String[] indices = getIndicesForRange(startTime, endTime);
            List<Map<String, Object>> results = esClient.searchByTimeRange(indices, startTime, endTime);
            
            return results.stream()
                    .map(this::fromDocument)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Failed to query Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch query failed", e);
        }
    }

    @Override
    public long countByTimeRange(Instant startTime, Instant endTime) {
        try {
            String[] indices = getIndicesForRange(startTime, endTime);
            return esClient.countByTimeRange(indices, startTime, endTime);
            
        } catch (Exception e) {
            log.error("Failed to count in Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch count failed", e);
        }
    }

    @Override
    public void deleteOlderThan(Instant timestamp) {
        try {
            // Delete old indices (efficient for time-based data)
            esClient.deleteIndicesOlderThan(INDEX_PREFIX, timestamp);
            log.info("Deleted Elasticsearch indices older than {}", timestamp);
            
        } catch (Exception e) {
            log.error("Failed to delete from Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch delete failed", e);
        }
    }

    /**
     * Generates index name based on timestamp.
     * Pattern: sql-audit-2024.01.15
     */
    private String getIndexName(Instant timestamp) {
        return INDEX_PREFIX + INDEX_DATE_FORMAT.format(timestamp.atZone(java.time.ZoneOffset.UTC));
    }

    /**
     * Returns array of index patterns for the given time range.
     */
    private String[] getIndicesForRange(Instant startTime, Instant endTime) {
        return new String[]{INDEX_PREFIX + "*"};
    }

    /**
     * Converts AuditEvent to Elasticsearch document.
     */
    private Map<String, Object> toDocument(AuditEvent event) {
        return Map.of(
                "event_id", UUID.randomUUID().toString(),
                "sql_id", event.getSqlId(),
                "sql_text", event.getSql(),
                "sql_type", event.getSqlType().name(),
                "mapper_id", event.getMapperId() != null ? event.getMapperId() : "",
                "datasource", event.getDatasource() != null ? event.getDatasource() : "",
                "execution_time_ms", event.getExecutionTimeMs(),
                "rows_affected", event.getRowsAffected(),
                "error_message", event.getErrorMessage() != null ? event.getErrorMessage() : "",
                "@timestamp", event.getTimestamp().toString()
        );
    }

    /**
     * Converts Elasticsearch document back to AuditEvent.
     */
    private AuditEvent fromDocument(Map<String, Object> doc) {
        return AuditEvent.builder()
                .sql((String) doc.get("sql_text"))
                .sqlType(SqlCommandType.valueOf((String) doc.get("sql_type")))
                .mapperId((String) doc.get("mapper_id"))
                .datasource((String) doc.get("datasource"))
                .executionTimeMs(((Number) doc.get("execution_time_ms")).longValue())
                .rowsAffected(((Number) doc.get("rows_affected")).intValue())
                .errorMessage((String) doc.get("error_message"))
                .timestamp(Instant.parse((String) doc.get("@timestamp")))
                .build();
    }
}

