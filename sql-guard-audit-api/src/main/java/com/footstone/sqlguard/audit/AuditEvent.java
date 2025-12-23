package com.footstone.sqlguard.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable data model representing an audit log event.
 *
 * <p>AuditEvent captures comprehensive information about a SQL execution including
 * the SQL statement, execution context, timing metrics, results, and any validation
 * violations detected during pre-execution validation.</p>
 *
 * <p><strong>Required Fields:</strong></p>
 * <ul>
 *   <li>{@code sql} - The SQL statement being audited</li>
 *   <li>{@code sqlType} - The SQL command type (SELECT, UPDATE, DELETE, INSERT)</li>
 *   <li>{@code mapperId} - The mapper identifier (e.g., "UserMapper.selectById")</li>
 *   <li>{@code timestamp} - The event timestamp</li>
 * </ul>
 *
 * <p><strong>Optional Fields:</strong></p>
 * <ul>
 *   <li>{@code datasource} - The datasource name (nullable)</li>
 *   <li>{@code params} - Parameter bindings (nullable)</li>
 *   <li>{@code executionTimeMs} - Execution time in milliseconds (default: 0)</li>
 *   <li>{@code rowsAffected} - Number of rows affected (default: -1 for not applicable)</li>
 *   <li>{@code errorMessage} - Error message if execution failed (nullable)</li>
 *   <li>{@code violations} - Pre-execution validation violations (nullable)</li>
 * </ul>
 *
 * <p><strong>SQL ID Generation:</strong></p>
 * <p>The {@code sqlId} field is automatically generated as the MD5 hash of the SQL
 * statement. This enables deduplication and efficient indexing of audit logs.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * AuditEvent event = AuditEvent.builder()
 *     .sql("SELECT * FROM users WHERE id = ?")
 *     .sqlType(SqlCommandType.SELECT)
 *     .mapperId("UserMapper.selectById")
 *     .datasource("primary")
 *     .timestamp(Instant.now())
 *     .executionTimeMs(150L)
 *     .rowsAffected(1)
 *     .build();
 * }</pre>
 *
 * @see AuditLogWriter
 * @see SqlCommandType
 * @see ValidationResult
 * @since 2.0.0
 */
public final class AuditEvent {

    /**
     * MD5 hash of the SQL statement for deduplication.
     */
    private final String sqlId;

    /**
     * The SQL statement being audited.
     */
    private final String sql;

    /**
     * The SQL command type (SELECT, UPDATE, DELETE, INSERT).
     */
    private final SqlCommandType sqlType;

    /**
     * The mapper identifier (e.g., "com.example.UserMapper.selectById").
     */
    private final String mapperId;

    /**
     * The datasource name (nullable).
     */
    private final String datasource;

    /**
     * Parameter bindings for the SQL statement (nullable).
     */
    private final Map<String, Object> params;

    /**
     * Execution time in milliseconds.
     */
    private final long executionTimeMs;

    /**
     * Number of rows affected (-1 if not applicable).
     */
    private final int rowsAffected;

    /**
     * Error message if execution failed (nullable).
     */
    private final String errorMessage;

    /**
     * Event timestamp.
     */
    private final Instant timestamp;

    /**
     * Pre-execution validation violations (nullable).
     */
    private final ValidationResult violations;

    /**
     * Private constructor for Builder pattern.
     */
    private AuditEvent(Builder builder) {
        this.sql = builder.sql;
        this.sqlType = builder.sqlType;
        this.mapperId = builder.mapperId;
        this.datasource = builder.datasource;
        this.params = builder.params;
        this.executionTimeMs = builder.executionTimeMs;
        this.rowsAffected = builder.rowsAffected;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp;
        this.violations = builder.violations;
        this.sqlId = generateSqlId(this.sql);
    }

    /**
     * Constructor for Jackson deserialization.
     * 
     * @param sqlId the SQL ID (will be regenerated from sql)
     * @param sql the SQL statement
     * @param sqlType the SQL command type
     * @param mapperId the mapper identifier
     * @param datasource the datasource name
     * @param params the parameter bindings
     * @param executionTimeMs the execution time in milliseconds
     * @param rowsAffected the number of rows affected
     * @param errorMessage the error message
     * @param timestamp the event timestamp
     * @param violations the validation violations
     */
    @JsonCreator
    private AuditEvent(
            @JsonProperty("sqlId") String sqlId,
            @JsonProperty("sql") String sql,
            @JsonProperty("sqlType") SqlCommandType sqlType,
            @JsonProperty("mapperId") String mapperId,
            @JsonProperty("datasource") String datasource,
            @JsonProperty("params") Map<String, Object> params,
            @JsonProperty("executionTimeMs") long executionTimeMs,
            @JsonProperty("rowsAffected") int rowsAffected,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("violations") ValidationResult violations) {
        this.sql = sql;
        this.sqlType = sqlType;
        this.mapperId = mapperId;
        this.datasource = datasource;
        this.params = params;
        this.executionTimeMs = executionTimeMs;
        this.rowsAffected = rowsAffected;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
        this.violations = violations;
        // Regenerate sqlId from sql (ignore the provided sqlId)
        this.sqlId = generateSqlId(this.sql);
    }

    /**
     * Generates MD5 hash of the SQL statement.
     *
     * @param sql the SQL statement
     * @return the MD5 hash as a hexadecimal string
     */
    private static String generateSqlId(String sql) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sql.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Creates a new builder for constructing AuditEvent instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getSqlId() {
        return sqlId;
    }

    public String getSql() {
        return sql;
    }

    public SqlCommandType getSqlType() {
        return sqlType;
    }

    public String getMapperId() {
        return mapperId;
    }

    public String getDatasource() {
        return datasource;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getRowsAffected() {
        return rowsAffected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ValidationResult getViolations() {
        return violations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return executionTimeMs == that.executionTimeMs &&
                rowsAffected == that.rowsAffected &&
                Objects.equals(sqlId, that.sqlId) &&
                Objects.equals(sql, that.sql) &&
                sqlType == that.sqlType &&
                Objects.equals(mapperId, that.mapperId) &&
                Objects.equals(datasource, that.datasource) &&
                Objects.equals(params, that.params) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(violations, that.violations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sqlId, sql, sqlType, mapperId, datasource, params,
                executionTimeMs, rowsAffected, errorMessage, timestamp, violations);
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "sqlId='" + sqlId + '\'' +
                ", sql='" + sql + '\'' +
                ", sqlType=" + sqlType +
                ", mapperId='" + mapperId + '\'' +
                ", datasource='" + datasource + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", rowsAffected=" + rowsAffected +
                ", errorMessage='" + errorMessage + '\'' +
                ", timestamp=" + timestamp +
                ", violations=" + violations +
                '}';
    }

    /**
     * Builder for constructing AuditEvent instances.
     *
     * <p>The builder validates all required fields and constraints before
     * constructing the immutable AuditEvent instance.</p>
     */
    public static class Builder {
        private String sql;
        private SqlCommandType sqlType;
        private String mapperId;
        private String datasource;
        private Map<String, Object> params;
        private long executionTimeMs = 0L;
        private int rowsAffected = -1;
        private String errorMessage;
        private Instant timestamp;
        private ValidationResult violations;

        private Builder() {
        }

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder sqlType(SqlCommandType sqlType) {
            this.sqlType = sqlType;
            return this;
        }

        public Builder mapperId(String mapperId) {
            this.mapperId = mapperId;
            return this;
        }

        public Builder datasource(String datasource) {
            this.datasource = datasource;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder rowsAffected(int rowsAffected) {
            this.rowsAffected = rowsAffected;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder violations(ValidationResult violations) {
            this.violations = violations;
            return this;
        }

        /**
         * Builds the AuditEvent instance after validating all constraints.
         *
         * <p><strong>Validation Rules:</strong></p>
         * <ul>
         *   <li>Required fields (sql, sqlType, mapperId, timestamp) must not be null</li>
         *   <li>executionTimeMs must be >= 0</li>
         *   <li>rowsAffected must be >= -1</li>
         *   <li>timestamp must not be in the future (with 5 second tolerance)</li>
         * </ul>
         *
         * @return the constructed AuditEvent instance
         * @throws IllegalArgumentException if validation fails
         */
        public AuditEvent build() {
            // Validate required fields
            if (sql == null) {
                throw new IllegalArgumentException("sql field is required");
            }
            if (sqlType == null) {
                throw new IllegalArgumentException("sqlType field is required");
            }
            if (mapperId == null) {
                throw new IllegalArgumentException("mapperId field is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp field is required");
            }

            // Validate constraints
            if (executionTimeMs < 0) {
                throw new IllegalArgumentException("executionTimeMs must be >= 0, got: " + executionTimeMs);
            }
            if (rowsAffected < -1) {
                throw new IllegalArgumentException("rowsAffected must be >= -1, got: " + rowsAffected);
            }

            // Validate timestamp not in future (with 5 second tolerance for clock skew)
            Instant now = Instant.now();
            Instant maxAllowed = now.plusSeconds(5);
            if (timestamp.isAfter(maxAllowed)) {
                throw new IllegalArgumentException(
                        "timestamp cannot be in the future. timestamp=" + timestamp + ", now=" + now);
            }

            return new AuditEvent(this);
        }
    }
}
