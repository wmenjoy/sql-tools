package com.footstone.sqlguard.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable data model capturing SQL execution metrics.
 * 
 * <p>Provides post-execution context such as rows affected, execution duration,
 * and error states, which are essential for audit analysis but unavailable
 * during pre-execution validation.</p>
 */
public final class ExecutionResult {

    private final int rowsAffected;
    private final long executionTimeMs;
    private final String errorMessage;
    private final Integer resultSetSize;
    private final Instant executionTimestamp;
    private final Map<String, Object> additionalMetrics;

    private ExecutionResult(Builder builder) {
        this.rowsAffected = builder.rowsAffected;
        this.executionTimeMs = builder.executionTimeMs;
        this.errorMessage = builder.errorMessage;
        this.resultSetSize = builder.resultSetSize;
        this.executionTimestamp = builder.executionTimestamp;
        this.additionalMetrics = builder.additionalMetrics != null 
                ? Collections.unmodifiableMap(new HashMap<>(builder.additionalMetrics)) 
                : Collections.emptyMap();
    }
    
    @JsonCreator
    public ExecutionResult(
            @JsonProperty("rowsAffected") int rowsAffected,
            @JsonProperty("executionTimeMs") long executionTimeMs,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("resultSetSize") Integer resultSetSize,
            @JsonProperty("executionTimestamp") Instant executionTimestamp,
            @JsonProperty("additionalMetrics") Map<String, Object> additionalMetrics) {
        this.rowsAffected = rowsAffected;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.resultSetSize = resultSetSize;
        this.executionTimestamp = executionTimestamp;
        this.additionalMetrics = additionalMetrics != null 
                ? Collections.unmodifiableMap(new HashMap<>(additionalMetrics)) 
                : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getRowsAffected() {
        return rowsAffected;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getResultSetSize() {
        return resultSetSize;
    }

    public Instant getExecutionTimestamp() {
        return executionTimestamp;
    }

    public Map<String, Object> getAdditionalMetrics() {
        return additionalMetrics;
    }
    
    @JsonIgnore
    public boolean isSuccess() {
        return errorMessage == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionResult that = (ExecutionResult) o;
        return rowsAffected == that.rowsAffected &&
                executionTimeMs == that.executionTimeMs &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(resultSetSize, that.resultSetSize) &&
                Objects.equals(executionTimestamp, that.executionTimestamp) &&
                Objects.equals(additionalMetrics, that.additionalMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rowsAffected, executionTimeMs, errorMessage, resultSetSize, executionTimestamp, additionalMetrics);
    }

    public static class Builder {
        private int rowsAffected = -1;
        private long executionTimeMs = 0;
        private String errorMessage;
        private Integer resultSetSize;
        private Instant executionTimestamp;
        private Map<String, Object> additionalMetrics;

        public Builder rowsAffected(int rowsAffected) {
            this.rowsAffected = rowsAffected;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder resultSetSize(Integer resultSetSize) {
            this.resultSetSize = resultSetSize;
            return this;
        }

        public Builder executionTimestamp(Instant executionTimestamp) {
            this.executionTimestamp = executionTimestamp;
            return this;
        }

        public Builder additionalMetrics(Map<String, Object> additionalMetrics) {
            this.additionalMetrics = additionalMetrics;
            return this;
        }

        public ExecutionResult build() {
            if (rowsAffected < -1) {
                throw new IllegalArgumentException("rowsAffected must be >= -1");
            }
            if (executionTimeMs < 0) {
                throw new IllegalArgumentException("executionTimeMs must be >= 0");
            }
            if (executionTimestamp == null) {
                throw new IllegalArgumentException("executionTimestamp is required");
            }
            return new ExecutionResult(this);
        }
    }
}
