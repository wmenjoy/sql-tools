package com.footstone.sqlguard.audit.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionResultTest {

    @Test
    void testBuilder_withAllMetrics_shouldConstruct() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cache_hit", true);
        Instant now = Instant.now();

        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(100)
                .executionTimeMs(50L)
                .resultSetSize(100)
                .executionTimestamp(now)
                .additionalMetrics(metrics)
                .build();

        assertEquals(100, result.getRowsAffected());
        assertEquals(50L, result.getExecutionTimeMs());
        assertEquals(100, result.getResultSetSize());
        assertEquals(now, result.getExecutionTimestamp());
        assertEquals(metrics, result.getAdditionalMetrics());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testBuilder_withErrorOnly_shouldConstructFailureResult() {
        Instant now = Instant.now();
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Connection failed")
                .executionTimestamp(now)
                .build();

        assertEquals("Connection failed", result.getErrorMessage());
        assertEquals(-1, result.getRowsAffected()); // Default
        assertEquals(0, result.getExecutionTimeMs()); // Default
        assertEquals(now, result.getExecutionTimestamp());
    }

    @Test
    void testRowsAffected_forDifferentOperations_shouldReflectActual() {
        ExecutionResult selectResult = ExecutionResult.builder()
                .rowsAffected(-1) // SELECT often -1 or 0 depending on driver
                .resultSetSize(50)
                .executionTimestamp(Instant.now())
                .build();
        
        assertEquals(-1, selectResult.getRowsAffected());
        assertEquals(50, selectResult.getResultSetSize());

        ExecutionResult updateResult = ExecutionResult.builder()
                .rowsAffected(5)
                .executionTimestamp(Instant.now())
                .build();
        
        assertEquals(5, updateResult.getRowsAffected());
    }

    @Test
    void testExecutionTime_shouldSupportMicrosecondPrecision() {
        // We use long for ms
        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(123456L)
                .executionTimestamp(Instant.now())
                .build();
        
        assertEquals(123456L, result.getExecutionTimeMs());
    }

    @Test
    void testEquals_withSameMetrics_shouldBeEqual() {
        Instant now = Instant.now();
        ExecutionResult r1 = ExecutionResult.builder()
                .rowsAffected(10)
                .executionTimeMs(20)
                .executionTimestamp(now)
                .build();
        
        ExecutionResult r2 = ExecutionResult.builder()
                .rowsAffected(10)
                .executionTimeMs(20)
                .executionTimestamp(now)
                .build();
        
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
    
    @Test
    void testValidation_shouldEnforceConstraints() {
        assertThrows(IllegalArgumentException.class, () -> 
            ExecutionResult.builder().rowsAffected(-2).build()
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            ExecutionResult.builder().executionTimeMs(-1).build()
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            ExecutionResult.builder().executionTimestamp(null).build()
        );
    }
}
