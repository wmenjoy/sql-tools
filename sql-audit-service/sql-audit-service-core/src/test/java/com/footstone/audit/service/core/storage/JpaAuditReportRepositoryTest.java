package com.footstone.audit.service.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.audit.service.core.storage.repository.AuditReportJpaRepository;
import com.footstone.audit.service.core.storage.repository.JpaAuditReportRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
class JpaAuditReportRepositoryTest {

    @SpringBootApplication
    @EntityScan(basePackages = "com.footstone.audit.service.core.storage.entity")
    @EnableJpaRepositories(basePackages = "com.footstone.audit.service.core.storage.repository")
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        public JpaAuditReportRepository jpaAuditReportRepository(AuditReportJpaRepository jpaRepository, ObjectMapper objectMapper) {
            return new JpaAuditReportRepository(jpaRepository, objectMapper);
        }
    }

    @Autowired
    private JpaAuditReportRepository repository;

    @Autowired
    private AuditReportJpaRepository jpaRepository;

    @Test
    void testSave_newReport_shouldPersist() {
        AuditReport report = createSampleReport();
        repository.save(report);
        
        assertTrue(jpaRepository.findById(report.reportId()).isPresent());
    }

    @Test
    void testSave_existingReport_shouldUpdate() {
        AuditReport report = createSampleReport();
        repository.save(report);
        
        // No update logic in instructions, but save typically overwrites in JPA
        // Let's verify it doesn't throw
        repository.save(report);
        assertEquals(1, jpaRepository.count());
    }

    @Test
    void testFindById_existingId_shouldReturn() {
        AuditReport report = createSampleReport();
        repository.save(report);
        
        Optional<AuditReport> found = repository.findById(report.reportId());
        assertTrue(found.isPresent());
        assertEquals(report.reportId(), found.get().reportId());
        assertEquals(report.sqlId(), found.get().sqlId());
    }

    @Test
    void testFindById_nonExistingId_shouldReturnEmpty() {
        Optional<AuditReport> found = repository.findById("non-existent");
        assertTrue(found.isEmpty());
    }

    @Test
    void testFindByTimeRange_shouldFilterCorrectly() {
        Instant now = Instant.now();
        AuditReport r1 = createSampleReport(now.minusSeconds(3600));
        AuditReport r2 = createSampleReport(now);
        
        repository.save(r1);
        repository.save(r2);
        
        List<AuditReport> results = repository.findByTimeRange(now.minusSeconds(10), now.plusSeconds(10));
        assertEquals(1, results.size());
        assertEquals(r2.reportId(), results.get(0).reportId());
    }

    @Test
    void testFindByTimeRange_emptyRange_shouldReturnEmpty() {
        Instant now = Instant.now();
        List<AuditReport> results = repository.findByTimeRange(now.minusSeconds(10), now.plusSeconds(10));
        assertTrue(results.isEmpty());
    }

    private AuditReport createSampleReport() {
        return createSampleReport(Instant.now());
    }

    private AuditReport createSampleReport(Instant createdAt) {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(createdAt)
                .build();

        return new AuditReport(
                UUID.randomUUID().toString(),
                event.getSqlId(),
                event,
                Collections.emptyList(),
                RiskScore.builder().severity(RiskLevel.LOW).justification("safe").build(),
                createdAt
        );
    }
}
