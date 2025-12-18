package com.footstone.sqlguard.audit.model;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskScoreTest {

    @Test
    void testBuilder_withAllDimensions_shouldConstruct() {
        Map<String, Number> metrics = new HashMap<>();
        metrics.put("data_volume_affected", 1000);
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Add index");

        RiskScore score = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .confidence(90)
                .impactMetrics(metrics)
                .justification("High data volume")
                .recommendations(recommendations)
                .build();

        assertEquals(RiskLevel.HIGH, score.getSeverity());
        assertEquals(90, score.getConfidence());
        assertEquals(1000, score.getImpactMetrics().get("data_volume_affected"));
        assertEquals("High data volume", score.getJustification());
        assertEquals(1, score.getRecommendations().size());
        assertEquals("Add index", score.getRecommendations().get(0));
    }

    @Test
    void testSeverity_shouldEnforceValidLevels() {
        assertThrows(IllegalArgumentException.class, () ->
                RiskScore.builder().severity(null).build()
        );
    }

    @Test
    void testConfidence_shouldEnforceRange() {
        assertThrows(IllegalArgumentException.class, () ->
                RiskScore.builder().severity(RiskLevel.LOW).confidence(-1).build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                RiskScore.builder().severity(RiskLevel.LOW).confidence(101).build()
        );

        // Valid range
        assertDoesNotThrow(() ->
                RiskScore.builder().severity(RiskLevel.LOW).confidence(0).justification("test").build()
        );
        assertDoesNotThrow(() ->
                RiskScore.builder().severity(RiskLevel.LOW).confidence(100).justification("test").build()
        );
    }

    @Test
    void testImpactMetrics_shouldBeImmutable() {
        Map<String, Number> metrics = new HashMap<>();
        metrics.put("score", 10);
        
        RiskScore score = RiskScore.builder()
                .severity(RiskLevel.LOW)
                .impactMetrics(metrics)
                .justification("test")
                .build();
        
        // Modify original map
        metrics.put("score", 20);
        assertEquals(10, score.getImpactMetrics().get("score"));
        
        // Try to modify getter result
        assertThrows(UnsupportedOperationException.class, () ->
                score.getImpactMetrics().put("new", 1)
        );
    }

    @Test
    void testCompareTo_shouldSortBySeverityThenConfidence() {
        // Critical > High
        RiskScore critical = RiskScore.builder()
                .severity(RiskLevel.CRITICAL)
                .confidence(50)
                .justification("c")
                .build();
                
        RiskScore high = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .confidence(100)
                .justification("h")
                .build();
        
        assertTrue(critical.compareTo(high) > 0); // Critical is "greater" risk
        
        // Same severity, higher confidence > lower confidence
        RiskScore highConf = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .confidence(90)
                .justification("h1")
                .build();
                
        RiskScore lowConf = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .confidence(50)
                .justification("h2")
                .build();
                
        assertTrue(highConf.compareTo(lowConf) > 0);
        
        // Equal
        RiskScore same = RiskScore.builder()
                .severity(RiskLevel.HIGH)
                .confidence(90)
                .justification("h1")
                .build();
        assertEquals(0, highConf.compareTo(same));
    }
}
