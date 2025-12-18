package com.footstone.audit.service.core.storage;

import com.footstone.audit.service.core.storage.entity.AuditReportEntity;
import com.footstone.audit.service.core.storage.entity.CheckerConfigEntity;
import com.footstone.audit.service.core.storage.repository.AuditReportJpaRepository;
import com.footstone.audit.service.core.storage.repository.CheckerConfigJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
class PostgreSQLSchemaTest {

    @SpringBootApplication
    @EntityScan(basePackages = "com.footstone.audit.service.core.storage.entity")
    @EnableJpaRepositories(basePackages = "com.footstone.audit.service.core.storage.repository")
    static class TestConfig {
    }

    @Autowired
    private AuditReportJpaRepository auditReportRepository;

    @Autowired
    private CheckerConfigJpaRepository checkerConfigRepository;

    @Test
    void testAuditReportEntity_shouldMapCorrectly() {
        AuditReportEntity entity = AuditReportEntity.builder()
            .reportId(UUID.randomUUID().toString())
            .sqlId("sql123")
            .originalEventJson("{}")
            .checkerResultsJson("[]")
            .riskLevel("HIGH")
            .riskScore(80)
            .createdAt(Instant.now())
            .build();

        AuditReportEntity saved = auditReportRepository.save(entity);
        
        assertNotNull(saved.getReportId());
        assertEquals("sql123", saved.getSqlId());
        assertEquals("HIGH", saved.getRiskLevel());
    }

    @Test
    void testAuditReportEntity_shouldGenerateId() {
        // ID is assigned manually in this entity design, but let's check it's preserved
        String id = UUID.randomUUID().toString();
        AuditReportEntity entity = AuditReportEntity.builder()
            .reportId(id)
            .sqlId("sql123")
            .riskScore(0)
            .createdAt(Instant.now())
            .build();

        auditReportRepository.save(entity);
        
        Optional<AuditReportEntity> found = auditReportRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getReportId());
    }

    @Test
    void testCheckerConfigEntity_shouldMapCorrectly() {
        CheckerConfigEntity entity = CheckerConfigEntity.builder()
            .checkerId("SlowChecker")
            .enabled(true)
            .build();

        checkerConfigRepository.save(entity);

        CheckerConfigEntity found = checkerConfigRepository.findById("SlowChecker").orElseThrow();
        assertTrue(found.isEnabled());
    }

    @Test
    void testJpaRepository_save_shouldPersist() {
        AuditReportEntity entity = AuditReportEntity.builder()
            .reportId(UUID.randomUUID().toString())
            .sqlId("testSql")
            .riskScore(10)
            .createdAt(Instant.now())
            .build();

        auditReportRepository.save(entity);
        assertEquals(1, auditReportRepository.count());
    }

    @Test
    void testJpaRepository_findById_shouldReturn() {
        String id = UUID.randomUUID().toString();
        AuditReportEntity entity = AuditReportEntity.builder()
            .reportId(id)
            .sqlId("testSql")
            .riskScore(10)
            .createdAt(Instant.now())
            .build();

        auditReportRepository.save(entity);

        Optional<AuditReportEntity> result = auditReportRepository.findById(id);
        assertTrue(result.isPresent());
        assertEquals("testSql", result.get().getSqlId());
    }

    @Test
    void testJpaRepository_findByTimeRange_shouldFilter() {
        Instant now = Instant.now();
        
        AuditReportEntity e1 = AuditReportEntity.builder()
            .reportId(UUID.randomUUID().toString())
            .createdAt(now.minusSeconds(3600))
            .riskScore(10)
            .build();
            
        AuditReportEntity e2 = AuditReportEntity.builder()
            .reportId(UUID.randomUUID().toString())
            .createdAt(now)
            .riskScore(10)
            .build();

        auditReportRepository.save(e1);
        auditReportRepository.save(e2);

        List<AuditReportEntity> results = auditReportRepository.findByCreatedAtBetween(
            now.minusSeconds(10), now.plusSeconds(10)
        );

        assertEquals(1, results.size());
        assertEquals(e2.getReportId(), results.get(0).getReportId());
    }
}
