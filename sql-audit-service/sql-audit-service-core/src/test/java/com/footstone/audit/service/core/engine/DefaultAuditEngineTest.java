package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.config.AuditEngineConfig;
import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DefaultAuditEngineTest {

    private DefaultAuditEngine engine;
    
    @Mock
    private AbstractAuditChecker checker1;
    
    @Mock
    private AuditEngineConfig config;

    @BeforeEach
    void setUp() {
        // lenient() because some tests might not use config
        org.mockito.Mockito.lenient().when(config.getCheckerTimeoutMs()).thenReturn(200L);
        org.mockito.Mockito.lenient().when(config.isCheckerEnabled(anyString())).thenReturn(true);
    }

    @Test
    void testAuditEngine_singleChecker_shouldExecute() {
        // Given
        engine = new DefaultAuditEngine(List.of(checker1), config);
        AuditEvent event = createEvent();
        
        RiskScore score = RiskScore.builder().severity(RiskLevel.LOW).justification("Test").build();
        AuditResult result = AuditResult.builder()
                .checkerId("checker1")
                .sql("sql")
                .executionResult(ExecutionResult.builder().executionTimestamp(Instant.now()).build())
                .addRisk(score)
                .auditTimestamp(Instant.now())
                .build();

        when(checker1.check(anyString(), any(ExecutionResult.class))).thenReturn(result);
        when(checker1.getCheckerId()).thenReturn("checker1");

        // When
        AuditProcessingResult processingResult = engine.process(event);

        // Then
        assertTrue(processingResult.success());
        assertNotNull(processingResult.report());
        assertEquals(1, processingResult.report().checkerResults().size());
        assertEquals(score, processingResult.report().checkerResults().get(0).riskScore());
    }
    
    @Test
    void testAuditEngine_multipleCheckers_shouldExecuteAll() {
        // Given
        AbstractAuditChecker checker2 = mock(AbstractAuditChecker.class);
        engine = new DefaultAuditEngine(List.of(checker1, checker2), config);
        AuditEvent event = createEvent();
        
        RiskScore score1 = RiskScore.builder().severity(RiskLevel.LOW).justification("Test1").build();
        AuditResult result1 = AuditResult.builder().checkerId("c1").sql("s").executionResult(ExecutionResult.builder().executionTimestamp(Instant.now()).build()).addRisk(score1).auditTimestamp(Instant.now()).build();
        
        RiskScore score2 = RiskScore.builder().severity(RiskLevel.HIGH).justification("Test2").build();
        AuditResult result2 = AuditResult.builder().checkerId("c2").sql("s").executionResult(ExecutionResult.builder().executionTimestamp(Instant.now()).build()).addRisk(score2).auditTimestamp(Instant.now()).build();

        when(checker1.check(anyString(), any())).thenReturn(result1);
        when(checker1.getCheckerId()).thenReturn("c1");
        
        when(checker2.check(anyString(), any())).thenReturn(result2);
        when(checker2.getCheckerId()).thenReturn("c2");

        // When
        AuditProcessingResult processingResult = engine.process(event);

        // Then
        assertEquals(2, processingResult.report().checkerResults().size());
    }

    @Test
    void testAuditEngine_checkerException_shouldContinue() {
        // Given
        engine = new DefaultAuditEngine(List.of(checker1), config);
        AuditEvent event = createEvent();

        when(checker1.check(anyString(), any())).thenThrow(new RuntimeException("Checker failed"));
        when(checker1.getCheckerId()).thenReturn("checker1");

        // When
        AuditProcessingResult processingResult = engine.process(event);

        // Then
        assertTrue(processingResult.success()); // Overall process still produces report
        assertEquals(1, processingResult.report().checkerResults().size());
        assertFalse(processingResult.report().checkerResults().get(0).isSuccess());
        assertEquals("Checker failed", processingResult.report().checkerResults().get(0).errorMessage());
    }

    @Test
    void testAuditEngine_emptyCheckers_shouldReturnEmptyReport() {
        // Given
        engine = new DefaultAuditEngine(Collections.emptyList(), config);
        AuditEvent event = createEvent();

        // When
        AuditProcessingResult processingResult = engine.process(event);

        // Then
        assertTrue(processingResult.success());
        assertTrue(processingResult.report().checkerResults().isEmpty());
    }
    
    @Test
    void testAuditEngine_resultAggregation_shouldCombineRiskScores() {
        // Given
        engine = new DefaultAuditEngine(List.of(checker1), config);
        AuditEvent event = createEvent();
        
        RiskScore score = RiskScore.builder().severity(RiskLevel.CRITICAL).justification("Critical Risk").build();
        AuditResult result = AuditResult.builder()
                .checkerId("checker1")
                .sql("sql")
                .executionResult(ExecutionResult.builder().executionTimestamp(Instant.now()).build())
                .addRisk(score)
                .auditTimestamp(Instant.now())
                .build();

        when(checker1.check(anyString(), any())).thenReturn(result);
        when(checker1.getCheckerId()).thenReturn("checker1");

        // When
        AuditProcessingResult processingResult = engine.process(event);

        // Then
        assertNotNull(processingResult.report().aggregatedRiskScore());
        assertEquals(RiskLevel.CRITICAL, processingResult.report().aggregatedRiskScore().getSeverity());
    }

    private AuditEvent createEvent() {
        return AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();
    }
}
