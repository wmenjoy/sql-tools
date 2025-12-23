package com.footstone.sqlguard.interceptor.jdbc.common;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.time.Instant;

/**
 * Utility class for building AuditEvent objects from JDBC interception context.
 *
 * <p>JdbcAuditEventBuilder provides static factory methods to create standardized
 * audit events for JDBC interceptors. This ensures consistent audit trail format
 * across all connection pool implementations.</p>
 *
 * <h2>Audit Event Contents</h2>
 * <p>Each audit event includes:</p>
 * <ul>
 *   <li><strong>Timestamp</strong> - When the SQL was intercepted</li>
 *   <li><strong>SQL</strong> - The intercepted SQL statement</li>
 *   <li><strong>MapperId</strong> - Identifier for tracking (jdbc.{type}:{datasource})</li>
 *   <li><strong>Datasource</strong> - The datasource name</li>
 *   <li><strong>SQL Type</strong> - SELECT, INSERT, UPDATE, DELETE, etc.</li>
 *   <li><strong>Violations</strong> - Validation result with any violations</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // After validation
 * SqlContext context = buildContext(...);
 * ValidationResult result = validator.validate(context);
 *
 * // Create audit event
 * AuditEvent event = JdbcAuditEventBuilder.createEvent(context, result);
 *
 * // Write to audit log
 * auditWriter.write(event);
 * }</pre>
 *
 * @since 2.0.0
 * @see AuditEvent
 * @see SqlContext
 * @see ValidationResult
 */
public final class JdbcAuditEventBuilder {

    /**
     * Private constructor to prevent instantiation.
     */
    private JdbcAuditEventBuilder() {
        // Utility class
    }

    /**
     * Creates an AuditEvent from SQL context and validation result.
     *
     * <p>This method captures all relevant information about the SQL execution
     * and its validation outcome in a standardized audit format.</p>
     *
     * @param context the SQL context containing SQL and metadata
     * @param result the validation result
     * @return constructed AuditEvent
     * @throws IllegalArgumentException if context or result is null
     */
    public static AuditEvent createEvent(SqlContext context, ValidationResult result) {
        if (context == null) {
            throw new IllegalArgumentException("SqlContext cannot be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("ValidationResult cannot be null");
        }

        return AuditEvent.builder()
            .timestamp(Instant.now())
            .sql(context.getSql())
            .sqlType(context.getType())
            .mapperId(context.getMapperId())
            .datasource(context.getDatasource())
            .violations(result)
            .build();
    }

    /**
     * Creates an AuditEvent with additional execution metadata.
     *
     * <p>Use this method when you have execution timing information available.</p>
     *
     * @param context the SQL context
     * @param result the validation result
     * @param executionTimeMs SQL execution time in milliseconds
     * @param rowsAffected number of rows affected (for UPDATE/DELETE)
     * @return constructed AuditEvent
     */
    public static AuditEvent createEvent(SqlContext context, ValidationResult result,
            long executionTimeMs, int rowsAffected) {
        if (context == null) {
            throw new IllegalArgumentException("SqlContext cannot be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("ValidationResult cannot be null");
        }

        return AuditEvent.builder()
            .timestamp(Instant.now())
            .sql(context.getSql())
            .sqlType(context.getType())
            .mapperId(context.getMapperId())
            .datasource(context.getDatasource())
            .violations(result)
            .executionTimeMs(executionTimeMs)
            .rowsAffected(rowsAffected)
            .build();
    }

    /**
     * Creates an AuditEvent for an error during SQL interception.
     *
     * <p>Use this method when an exception occurred during validation or execution.</p>
     *
     * @param context the SQL context (may be partially populated)
     * @param error the exception that occurred
     * @return constructed AuditEvent
     */
    public static AuditEvent createErrorEvent(SqlContext context, Throwable error) {
        AuditEvent.Builder builder = AuditEvent.builder()
            .timestamp(Instant.now())
            .errorMessage(error != null ? error.getMessage() : "Unknown error");

        if (context != null) {
            builder.sql(context.getSql())
                .sqlType(context.getType())
                .mapperId(context.getMapperId())
                .datasource(context.getDatasource());
        } else {
            // Provide defaults for required fields
            builder.sql("UNKNOWN")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.UNKNOWN)
                .mapperId("jdbc.error:unknown");
        }

        return builder.build();
    }

    /**
     * Creates a new fluent builder for AuditEvent.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for creating customized AuditEvents.
     */
    public static class Builder {
        private SqlContext context;
        private ValidationResult result;
        private Long executionTimeMs;
        private Integer rowsAffected;

        /**
         * Sets the SQL context.
         *
         * @param context the SQL context
         * @return this builder
         */
        public Builder context(SqlContext context) {
            this.context = context;
            return this;
        }

        /**
         * Sets the validation result.
         *
         * @param result the validation result
         * @return this builder
         */
        public Builder result(ValidationResult result) {
            this.result = result;
            return this;
        }

        /**
         * Sets the execution time.
         *
         * @param executionTimeMs execution time in milliseconds
         * @return this builder
         */
        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        /**
         * Sets the affected row count.
         *
         * @param rowsAffected number of affected rows
         * @return this builder
         */
        public Builder rowsAffected(int rowsAffected) {
            this.rowsAffected = rowsAffected;
            return this;
        }

        /**
         * Builds the AuditEvent.
         *
         * @return constructed AuditEvent
         * @throws IllegalStateException if required fields are missing
         */
        public AuditEvent build() {
            if (context == null || result == null) {
                throw new IllegalStateException("Context and result are required");
            }

            if (executionTimeMs != null && rowsAffected != null) {
                return createEvent(context, result, executionTimeMs, rowsAffected);
            } else {
                return createEvent(context, result);
            }
        }
    }
}






