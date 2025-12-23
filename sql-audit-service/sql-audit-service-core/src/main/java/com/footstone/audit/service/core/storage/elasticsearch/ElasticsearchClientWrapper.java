package com.footstone.audit.service.core.storage.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wrapper for Elasticsearch Java Client providing audit-specific operations.
 *
 * <h2>Purpose</h2>
 * <p>Encapsulates Elasticsearch client operations with:
 * <ul>
 *   <li>Index management (create, delete)</li>
 *   <li>Document indexing (single and bulk)</li>
 *   <li>Time-range queries</li>
 *   <li>Index lifecycle operations</li>
 * </ul>
 *
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchClientWrapper {

    private final ElasticsearchClient client;
    
    private static final DateTimeFormatter INDEX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * Indexes a single document.
     *
     * @param indexName Index name
     * @param document Document as Map
     */
    @SuppressWarnings("unchecked")
    public void index(String indexName, Map<String, Object> document) throws IOException {
        IndexRequest<Map<String, Object>> request = IndexRequest.of(builder -> builder
                .index(indexName)
                .document(document)
        );
        
        client.index(request);
    }

    /**
     * Bulk indexes multiple documents.
     *
     * @param indexName Index name
     * @param documents List of documents
     */
    public void bulkIndex(String indexName, List<Map<String, Object>> documents) throws IOException {
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        
        for (Map<String, Object> doc : documents) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .document(doc)
                    )
            );
        }
        
        BulkResponse response = client.bulk(bulkBuilder.build());
        
        if (response.errors()) {
            log.error("Bulk indexing had errors");
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Error: {}", item.error().reason()));
        }
    }

    /**
     * Searches documents by time range.
     *
     * @param indices Index names or patterns
     * @param startTime Start time (inclusive)
     * @param endTime End time (inclusive)
     * @return List of matching documents
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchByTimeRange(String[] indices, Instant startTime, Instant endTime) 
            throws IOException {
        
        Query rangeQuery = Query.of(q -> q
                .range(RangeQuery.of(r -> r
                        .field("@timestamp")
                        .gte(JsonData.of(startTime.toString()))
                        .lte(JsonData.of(endTime.toString()))
                ))
        );
        
        SearchRequest request = SearchRequest.of(builder -> builder
                .index(List.of(indices))
                .query(rangeQuery)
                .size(10000) // Max results
                .sort(s -> s.field(f -> f.field("@timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );
        
        SearchResponse<Map> response = client.search(request, Map.class);
        
        List<Map<String, Object>> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                results.add(hit.source());
            }
        }
        
        return results;
    }

    /**
     * Counts documents by time range.
     *
     * @param indices Index names or patterns
     * @param startTime Start time (inclusive)
     * @param endTime End time (inclusive)
     * @return Document count
     */
    public long countByTimeRange(String[] indices, Instant startTime, Instant endTime) throws IOException {
        Query rangeQuery = Query.of(q -> q
                .range(RangeQuery.of(r -> r
                        .field("@timestamp")
                        .gte(JsonData.of(startTime.toString()))
                        .lte(JsonData.of(endTime.toString()))
                ))
        );
        
        CountRequest request = CountRequest.of(builder -> builder
                .index(List.of(indices))
                .query(rangeQuery)
        );
        
        CountResponse response = client.count(request);
        return response.count();
    }

    /**
     * Deletes indices older than specified timestamp.
     *
     * @param indexPrefix Index prefix (e.g., "sql-audit-")
     * @param beforeTimestamp Delete indices with dates before this timestamp
     */
    public void deleteIndicesOlderThan(String indexPrefix, Instant beforeTimestamp) throws IOException {
        // Get all matching indices
        GetIndexRequest getRequest = GetIndexRequest.of(builder -> builder
                .index(indexPrefix + "*")
        );
        
        Set<String> existingIndices = client.indices().get(getRequest).result().keySet();
        
        // Filter indices older than threshold
        String thresholdDate = INDEX_DATE_FORMAT.format(beforeTimestamp.atZone(java.time.ZoneOffset.UTC));
        
        List<String> indicesToDelete = existingIndices.stream()
                .filter(idx -> {
                    String dateStr = idx.replace(indexPrefix, "");
                    return dateStr.compareTo(thresholdDate) < 0;
                })
                .collect(Collectors.toList());
        
        if (!indicesToDelete.isEmpty()) {
            DeleteIndexRequest deleteRequest = DeleteIndexRequest.of(builder -> builder
                    .index(indicesToDelete)
            );
            
            client.indices().delete(deleteRequest);
            log.info("Deleted {} old Elasticsearch indices", indicesToDelete.size());
        }
    }
}

