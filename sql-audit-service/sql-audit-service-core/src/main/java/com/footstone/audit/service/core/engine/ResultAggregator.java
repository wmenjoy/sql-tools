package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ResultAggregator {

    public RiskScore aggregate(List<CheckerResult> results) {
        return results.stream()
                .filter(CheckerResult::isSuccess)
                .map(CheckerResult::riskScore)
                .filter(score -> score != null)
                .max(Comparator.comparing(RiskScore::getSeverity))
                .orElse(RiskScore.builder()
                        .severity(RiskLevel.SAFE)
                        .justification("No risks detected")
                        .build());
    }
}
