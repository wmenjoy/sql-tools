package com.footstone.audit.service.core.model;

import com.footstone.audit.service.core.processor.AuditEventProcessor;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuditEngineModelTest {

    @Test
    void testAuditReport_builder_shouldCreateImmutable() {
        // Given
        String reportId = "rep-123";
        String sqlId = "sql-123";
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
                .statementId("test")
                .timestamp(Instant.now())
                .build();
        List<CheckerResult> checkerResults = Collections.emptyList();
        RiskScore aggregatedRiskScore = RiskScore.builder()
                .severity(RiskLevel.LOW)
                .justification("Aggregation")
                .build();
        Instant createdAt = Instant.now();

        // When
        AuditReport report = new AuditReport(reportId, sqlId, event, checkerResults, aggregatedRiskScore, createdAt);

        // Then
        assertEquals(reportId, report.reportId());
        assertEquals(sqlId, report.sqlId());
        assertEquals(event, report.originalEvent());
        assertEquals(checkerResults, report.checkerResults());
        assertEquals(aggregatedRiskScore, report.aggregatedRiskScore());
        assertEquals(createdAt, report.createdAt());
    }

    @Test
    void testCheckerResult_shouldContainRiskScore() {
        // Given
        String checkerId = "TestChecker";
        RiskScore score = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .justification("Test Risk")
                .build();
        
        // When
        CheckerResult result = new CheckerResult(checkerId, score, null);

        // Then
        assertEquals(checkerId, result.checkerId());
        assertEquals(score, result.riskScore());
    }
    
    @Test
    void testAuditProcessingResult_shouldContainReport() {
        // Given
        AuditReport report = new AuditReport("id", "sql", null, null, null, Instant.now());
        boolean success = true;
        
        // When
        AuditProcessingResult result = new AuditProcessingResult(success, report, null);
        
        // Then
        assertTrue(result.success());
        assertEquals(report, result.report());
    }

    @Test
    void testAuditEventProcessor_interface_shouldDefine() {
        // Verify interface definition via anonymous class
        AuditEventProcessor processor = new AuditEventProcessor() {
            @Override
            public AuditProcessingResult process(AuditEvent event) {
                return null;
            }
        };
        assertNotNull(processor);
    }

    @Test
    void testAuditReportRepository_interface_shouldDefine() {
        // Verify interface definition via anonymous class
        AuditReportRepository repository = new AuditReportRepository() {
            @Override
            public void save(AuditReport report) {}

            @Override
            public Optional<AuditReport> findById(String reportId) {
                return Optional.empty();
            }

            @Override
            public List<AuditReport> findByTimeRange(Instant start, Instant end) {
                return Collections.emptyList();
            }
        };
        assertNotNull(repository);
    }
}
