package com.footstone.sqlguard.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.*;

/**
 * Multi-dimensional risk assessment score.
 * 
 * <p>Combines severity level with confidence score and quantitative impact metrics
 * to provide a nuanced risk assessment. Implements {@link Comparable} to support
 * prioritization of findings.</p>
 */
public final class RiskScore implements Comparable<RiskScore> {

    private final RiskLevel severity;
    private final int confidence;
    private final Map<String, Number> impactMetrics;
    private final String justification;
    private final List<String> recommendations;

    private RiskScore(Builder builder) {
        this.severity = builder.severity;
        this.confidence = builder.confidence;
        this.impactMetrics = builder.impactMetrics != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.impactMetrics))
                : Collections.emptyMap();
        this.justification = builder.justification;
        this.recommendations = builder.recommendations != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.recommendations))
                : Collections.emptyList();
    }

    @JsonCreator
    public RiskScore(
            @JsonProperty("severity") RiskLevel severity,
            @JsonProperty("confidence") int confidence,
            @JsonProperty("impactMetrics") Map<String, Number> impactMetrics,
            @JsonProperty("justification") String justification,
            @JsonProperty("recommendations") List<String> recommendations) {
        this.severity = severity;
        this.confidence = confidence;
        this.impactMetrics = impactMetrics != null
                ? Collections.unmodifiableMap(new HashMap<>(impactMetrics))
                : Collections.emptyMap();
        this.justification = justification;
        this.recommendations = recommendations != null
                ? Collections.unmodifiableList(new ArrayList<>(recommendations))
                : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public RiskLevel getSeverity() {
        return severity;
    }

    public int getConfidence() {
        return confidence;
    }

    public Map<String, Number> getImpactMetrics() {
        return impactMetrics;
    }

    public String getJustification() {
        return justification;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    @Override
    public int compareTo(RiskScore other) {
        if (other == null) return 1;
        
        // Primary sort: Severity (higher ordinal = higher risk)
        int severityCompare = this.severity.compareTo(other.severity);
        if (severityCompare != 0) {
            return severityCompare;
        }
        
        // Secondary sort: Confidence (higher confidence = higher priority within same severity)
        return Integer.compare(this.confidence, other.confidence);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskScore riskScore = (RiskScore) o;
        return confidence == riskScore.confidence &&
                severity == riskScore.severity &&
                Objects.equals(impactMetrics, riskScore.impactMetrics) &&
                Objects.equals(justification, riskScore.justification) &&
                Objects.equals(recommendations, riskScore.recommendations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, confidence, impactMetrics, justification, recommendations);
    }

    public static class Builder {
        private RiskLevel severity;
        private int confidence = 100; // Default to 100% confidence
        private Map<String, Number> impactMetrics;
        private String justification;
        private List<String> recommendations;

        public Builder severity(RiskLevel severity) {
            this.severity = severity;
            return this;
        }

        public Builder confidence(int confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder impactMetrics(Map<String, Number> impactMetrics) {
            this.impactMetrics = impactMetrics;
            return this;
        }

        public Builder justification(String justification) {
            this.justification = justification;
            return this;
        }

        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations;
            return this;
        }
        
        public Builder addRecommendation(String recommendation) {
            if (this.recommendations == null) {
                this.recommendations = new ArrayList<>();
            }
            this.recommendations.add(recommendation);
            return this;
        }

        public RiskScore build() {
            if (severity == null) {
                throw new IllegalArgumentException("severity is required");
            }
            if (confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("confidence must be between 0 and 100");
            }
            if (justification == null || justification.trim().isEmpty()) {
                throw new IllegalArgumentException("justification is required");
            }
            return new RiskScore(this);
        }
    }
}
