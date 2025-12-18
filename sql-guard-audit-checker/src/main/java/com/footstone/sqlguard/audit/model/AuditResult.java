package com.footstone.sqlguard.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated audit result from a checker.
 */
public class AuditResult {
    private final String checkerId;
    private final String sql;
    private final ExecutionResult executionResult;
    private final List<RiskScore> risks;
    private final Instant auditTimestamp;
    private final Map<String, Object> checkerMetadata;

    private AuditResult(Builder builder) {
        this.checkerId = builder.checkerId;
        this.sql = builder.sql;
        this.executionResult = builder.executionResult;
        this.risks = builder.risks != null ? Collections.unmodifiableList(new ArrayList<>(builder.risks)) : Collections.emptyList();
        this.auditTimestamp = builder.auditTimestamp;
        this.checkerMetadata = builder.checkerMetadata != null ? Collections.unmodifiableMap(new HashMap<>(builder.checkerMetadata)) : Collections.emptyMap();
    }

    @JsonCreator
    public AuditResult(
            @JsonProperty("checkerId") String checkerId,
            @JsonProperty("sql") String sql,
            @JsonProperty("executionResult") ExecutionResult executionResult,
            @JsonProperty("risks") List<RiskScore> risks,
            @JsonProperty("auditTimestamp") Instant auditTimestamp,
            @JsonProperty("checkerMetadata") Map<String, Object> checkerMetadata) {
        this.checkerId = checkerId;
        this.sql = sql;
        this.executionResult = executionResult;
        this.risks = risks != null ? Collections.unmodifiableList(new ArrayList<>(risks)) : Collections.emptyList();
        this.auditTimestamp = auditTimestamp;
        this.checkerMetadata = checkerMetadata != null ? Collections.unmodifiableMap(new HashMap<>(checkerMetadata)) : Collections.emptyMap();
    }

    public String getCheckerId() { return checkerId; }
    public String getSql() { return sql; }
    public ExecutionResult getExecutionResult() { return executionResult; }
    public List<RiskScore> getRisks() { return risks; }
    public Instant getAuditTimestamp() { return auditTimestamp; }
    public Map<String, Object> getCheckerMetadata() { return checkerMetadata; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String checkerId;
        private String sql;
        private ExecutionResult executionResult;
        private List<RiskScore> risks = new ArrayList<>();
        private Instant auditTimestamp;
        private Map<String, Object> checkerMetadata;

        public Builder checkerId(String checkerId) { this.checkerId = checkerId; return this; }
        public Builder sql(String sql) { this.sql = sql; return this; }
        public Builder executionResult(ExecutionResult executionResult) { this.executionResult = executionResult; return this; }
        public Builder addRisk(RiskScore risk) { 
            if (risk != null) this.risks.add(risk); 
            return this; 
        }
        public Builder risks(List<RiskScore> risks) {
            this.risks = risks != null ? new ArrayList<>(risks) : new ArrayList<>();
            return this;
        }
        public Builder auditTimestamp(Instant auditTimestamp) { this.auditTimestamp = auditTimestamp; return this; }
        public Builder checkerMetadata(Map<String, Object> checkerMetadata) { this.checkerMetadata = checkerMetadata; return this; }
        
        public AuditResult build() {
            if (checkerId == null) throw new IllegalArgumentException("checkerId is required");
            if (sql == null) throw new IllegalArgumentException("sql is required");
            if (executionResult == null) throw new IllegalArgumentException("executionResult is required");
            if (auditTimestamp == null) throw new IllegalArgumentException("auditTimestamp is required");
            return new AuditResult(this);
        }
    }
}
