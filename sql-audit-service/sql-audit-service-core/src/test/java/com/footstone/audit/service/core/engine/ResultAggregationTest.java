package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultAggregationTest {

    private final ResultAggregator aggregator = new ResultAggregator();

    @Test
    void testAggregation_multipleRiskScores_shouldCombine() {
        CheckerResult r1 = CheckerResult.success("c1", RiskScore.builder().severity(RiskLevel.LOW).justification("low").build());
        CheckerResult r2 = CheckerResult.success("c2", RiskScore.builder().severity(RiskLevel.HIGH).justification("high").build());
        
        RiskScore result = aggregator.aggregate(List.of(r1, r2));
        
        assertEquals(RiskLevel.HIGH, result.getSeverity());
    }

    @Test
    void testAggregation_emptyResults_shouldReturnNoRisk() {
        RiskScore result = aggregator.aggregate(List.of());
        assertEquals(RiskLevel.SAFE, result.getSeverity());
        assertEquals("No risks detected", result.getJustification());
    }
}
