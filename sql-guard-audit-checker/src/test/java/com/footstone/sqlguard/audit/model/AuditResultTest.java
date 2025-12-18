package com.footstone.sqlguard.audit.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditResultTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void testBuilder_withCompleteData_shouldConstruct() {
        ExecutionResult execResult = ExecutionResult.builder()
                .rowsAffected(10)
                .executionTimeMs(50)
                .executionTimestamp(Instant.now())
                .build();
                
        RiskScore risk = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .justification("Test")
                .build();
                
        Instant auditTime = Instant.now();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("threshold", 100);

        AuditResult result = AuditResult.builder()
                .checkerId("TestChecker")
                .sql("SELECT 1")
                .executionResult(execResult)
                .addRisk(risk)
                .auditTimestamp(auditTime)
                .checkerMetadata(metadata)
                .build();

        assertEquals("TestChecker", result.getCheckerId());
        assertEquals("SELECT 1", result.getSql());
        assertEquals(execResult, result.getExecutionResult());
        assertEquals(1, result.getRisks().size());
        assertEquals(risk, result.getRisks().get(0));
        assertEquals(auditTime, result.getAuditTimestamp());
        assertEquals(100, result.getCheckerMetadata().get("threshold"));
    }

    @Test
    void testBuilder_withMultipleRisks_shouldAggregate() {
        RiskScore r1 = RiskScore.builder().severity(RiskLevel.LOW).justification("r1").build();
        RiskScore r2 = RiskScore.builder().severity(RiskLevel.HIGH).justification("r2").build();

        AuditResult result = AuditResult.builder()
                .checkerId("TestChecker")
                .sql("SELECT 1")
                .executionResult(ExecutionResult.builder().executionTimestamp(Instant.now()).build())
                .addRisk(r1)
                .addRisk(r2)
                .auditTimestamp(Instant.now())
                .build();

        assertEquals(2, result.getRisks().size());
        assertTrue(result.getRisks().contains(r1));
        assertTrue(result.getRisks().contains(r2));
    }

    @Test
    void testToJson_shouldSerializeForStorage() throws JsonProcessingException {
        ExecutionResult execResult = ExecutionResult.builder()
                .rowsAffected(5)
                .executionTimestamp(Instant.now())
                .build();
                
        AuditResult result = AuditResult.builder()
                .checkerId("SerializationChecker")
                .sql("SELECT * FROM table")
                .executionResult(execResult)
                .addRisk(RiskScore.builder().severity(RiskLevel.MEDIUM).justification("J").build())
                .auditTimestamp(Instant.now())
                .build();

        String json = objectMapper.writeValueAsString(result);
        
        assertNotNull(json);
        assertTrue(json.contains("SerializationChecker"));
        assertTrue(json.contains("MEDIUM"));
        
        // Round trip
        AuditResult deserialized = objectMapper.readValue(json, AuditResult.class);
        assertEquals(result.getCheckerId(), deserialized.getCheckerId());
        assertEquals(result.getSql(), deserialized.getSql());
        assertEquals(result.getRisks().size(), deserialized.getRisks().size());
    }

    @Test
    void testValidation_shouldRequireMandatoryFields() {
        assertThrows(IllegalArgumentException.class, () ->
            AuditResult.builder().build() // Missing everything
        );
        
        assertThrows(IllegalArgumentException.class, () ->
            AuditResult.builder().checkerId("C").build() // Missing SQL
        );
    }
}
