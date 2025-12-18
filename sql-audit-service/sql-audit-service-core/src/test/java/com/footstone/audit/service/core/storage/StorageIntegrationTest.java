package com.footstone.audit.service.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.audit.service.core.storage.adapter.PostgreSQLOnlyStorageAdapter;
import com.footstone.audit.service.core.storage.repository.AuditReportJpaRepository;
import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import com.footstone.audit.service.core.storage.repository.JpaAuditReportRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
class StorageIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "com.footstone.audit.service.core.storage.repository")
    @EntityScan(basePackages = "com.footstone.audit.service.core.storage.entity")
    static class Config {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper;
        }
        
        @Bean
        public PostgreSQLOnlyStorageAdapter executionLogRepository(JdbcTemplate jdbcTemplate) {
            return new PostgreSQLOnlyStorageAdapter(jdbcTemplate);
        }

        @Bean
        public JpaAuditReportRepository auditReportRepository(AuditReportJpaRepository jpaRepository, ObjectMapper objectMapper) {
            return new JpaAuditReportRepository(jpaRepository, objectMapper);
        }
    }

    @Autowired
    private AuditReportRepository reportRepository;

    @Autowired
    private ExecutionLogRepository logRepository;

    @Test
    void testFullPipeline_auditReportStorage_shouldWork() {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();
        
        RiskScore score = RiskScore.builder()
                .severity(RiskLevel.LOW)
                .confidence(10)
                .justification("test")
                .build();

        AuditReport report = new AuditReport(
                UUID.randomUUID().toString(),
                "test-sql-id", // Use a dummy ID or event.getSqlId() if accessible
                event,
                Collections.emptyList(),
                score,
                Instant.now()
        );
        
        reportRepository.save(report);
        
        AuditReport loaded = reportRepository.findById(report.reportId()).orElse(null);
        assertNotNull(loaded);
        assertEquals(report.reportId(), loaded.reportId());
    }

    @Test
    void testFullPipeline_executionLogging_shouldWork() {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();
        
        logRepository.log(event);
        
        List<AuditEvent> events = logRepository.findByTimeRange(Instant.now().minusSeconds(10), Instant.now().plusSeconds(1));
        assertEquals(1, events.size());
    }
}
