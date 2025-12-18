package com.footstone.audit.service.core.storage;

import com.footstone.audit.service.core.storage.adapter.PostgreSQLOnlyStorageAdapter;
import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "audit.storage.mode=postgresql-only",
    "spring.flyway.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Transactional
class PostgreSQLOnlyModeTest {

    @SpringBootApplication
    static class TestConfig {
        @Bean
        public PostgreSQLOnlyStorageAdapter postgreSQLOnlyStorageAdapter(JdbcTemplate jdbcTemplate) {
            return new PostgreSQLOnlyStorageAdapter(jdbcTemplate);
        }

        @Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }

    @Autowired
    private ExecutionLogRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testPostgreSQLOnlyMode_shouldStoreExecutions() {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();

        repository.log(event);

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM sql_executions_pg", Integer.class);
        assertEquals(1, count);
        
        Map<String, Object> map = jdbcTemplate.queryForMap("SELECT * FROM sql_executions_pg LIMIT 1");
        assertEquals("SELECT 1", map.get("sql"));
    }

    @Test
    void testPostgreSQLOnlyMode_batchInsert() {
        AuditEvent e1 = AuditEvent.builder().sql("S1").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(Instant.now()).build();
        AuditEvent e2 = AuditEvent.builder().sql("S2").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(Instant.now()).build();

        repository.logBatch(List.of(e1, e2));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM sql_executions_pg WHERE sql IN ('S1', 'S2')", Integer.class);
        assertEquals(2, count);
    }

    @Test
    void testPostgreSQLOnlyMode_shouldQueryByTimeRange() {
        Instant now = Instant.now();
        AuditEvent e1 = AuditEvent.builder().sql("S1").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now.minusSeconds(10)).build();
        AuditEvent e2 = AuditEvent.builder().sql("S2").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now.minusSeconds(30)).build();
        repository.logBatch(List.of(e1, e2));

        List<AuditEvent> result = repository.findByTimeRange(now.minusSeconds(20), now);
        assertEquals(1, result.size());
        assertEquals("S1", result.get(0).getSql());
    }

    @Test
    void testPostgreSQLOnlyMode_shouldSupportAggregation() {
        Instant now = Instant.now();
        AuditEvent e1 = AuditEvent.builder().sql("S1").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now.minusSeconds(10)).build();
        AuditEvent e2 = AuditEvent.builder().sql("S2").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now.minusSeconds(5)).build();
        repository.logBatch(List.of(e1, e2));

        long count = repository.countByTimeRange(now.minusSeconds(20), now);
        assertEquals(2, count);
    }

    @Test
    void testPostgreSQLOnlyMode_retention_shouldDeleteOldData() {
        Instant now = Instant.now();
        AuditEvent e1 = AuditEvent.builder().sql("Old").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now.minusSeconds(100)).build();
        AuditEvent e2 = AuditEvent.builder().sql("New").sqlType(SqlCommandType.SELECT).mapperId("m").timestamp(now).build();
        repository.logBatch(List.of(e1, e2));

        repository.deleteOlderThan(now.minusSeconds(50));

        long count = repository.countByTimeRange(now.minusSeconds(200), now.plusSeconds(1));
        assertEquals(1, count);
    }
}
