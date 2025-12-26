package com.footstone.audit.service.core.storage;

import com.footstone.audit.service.core.storage.elasticsearch.ElasticsearchClientWrapper;
import com.footstone.audit.service.core.storage.elasticsearch.ElasticsearchStorageAdapter;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ElasticsearchStorageAdapter.
 *
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchStorageAdapterTest {

    @Mock
    private ElasticsearchClientWrapper esClient;

    private ElasticsearchStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ElasticsearchStorageAdapter(esClient);
    }

    @Test
    void testLog_singleEvent_shouldIndex() throws Exception {
        // Given
        AuditEvent event = createTestEvent("SELECT * FROM users");

        // When
        adapter.log(event);

        // Then
        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> docCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(esClient, times(1)).index(indexCaptor.capture(), docCaptor.capture());
        
        String indexName = indexCaptor.getValue();
        assertTrue(indexName.startsWith("sql-audit-"), "Index should start with 'sql-audit-'");
        
        Map<String, Object> document = docCaptor.getValue();
        assertEquals(event.getSql(), document.get("sql_text"));
        assertEquals("SELECT", document.get("sql_type"));
    }

    @Test
    void testLogBatch_multipleEvents_shouldBulkIndex() throws Exception {
        // Given
        List<AuditEvent> events = Arrays.asList(
                createTestEvent("SELECT * FROM users"),
                createTestEvent("UPDATE users SET name = ?"),
                createTestEvent("DELETE FROM users WHERE id = ?")
        );

        // When
        adapter.logBatch(events);

        // Then
        verify(esClient, atLeastOnce()).bulkIndex(anyString(), anyList());
    }

    @Test
    void testLogBatch_emptyList_shouldDoNothing() throws Exception {
        // Given
        List<AuditEvent> events = List.of();

        // When
        adapter.logBatch(events);

        // Then
        verifyNoInteractions(esClient);
    }

    @Test
    void testFindByTimeRange_shouldSearchAndConvert() throws Exception {
        // Given
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        
        Map<String, Object> doc = Map.of(
                "sql_text", "SELECT * FROM users",
                "sql_type", "SELECT",
                "mapper_id", "UserMapper.findAll",
                "datasource", "primary",
                "execution_time_ms", 100L,
                "rows_affected", 10,
                "error_message", "",
                "@timestamp", start.toString()
        );
        
        when(esClient.searchByTimeRange(any(String[].class), eq(start), eq(end)))
                .thenReturn(List.of(doc));

        // When
        List<AuditEvent> results = adapter.findByTimeRange(start, end);

        // Then
        assertEquals(1, results.size());
        assertEquals("SELECT * FROM users", results.get(0).getSql());
        assertEquals(SqlCommandType.SELECT, results.get(0).getSqlType());
    }

    @Test
    void testCountByTimeRange_shouldReturnCount() throws Exception {
        // Given
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        when(esClient.countByTimeRange(any(String[].class), eq(start), eq(end)))
                .thenReturn(42L);

        // When
        long count = adapter.countByTimeRange(start, end);

        // Then
        assertEquals(42L, count);
    }

    @Test
    void testDeleteOlderThan_shouldDeleteIndices() throws Exception {
        // Given
        Instant threshold = Instant.now().minusSeconds(86400 * 90);

        // When
        adapter.deleteOlderThan(threshold);

        // Then
        verify(esClient, times(1)).deleteIndicesOlderThan(eq("sql-audit-"), eq(threshold));
    }

    @Test
    void testLog_withError_shouldThrowRuntimeException() throws Exception {
        // Given
        AuditEvent event = createTestEvent("SELECT * FROM users");
        doThrow(new RuntimeException("ES error"))
                .when(esClient)
                .index(anyString(), any());

        // When/Then
        assertThrows(RuntimeException.class, () -> adapter.log(event));
    }

    private AuditEvent createTestEvent(String sql) {
        return AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT) // Default to SELECT for tests
                .statementId("TestMapper.method")
                .datasource("testdb")
                .executionTimeMs(100L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();
    }
}

