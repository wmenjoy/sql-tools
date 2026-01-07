package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.footstone.sqlguard.core.model.ValidationResult;

/**
 * Exception thrown when SQL safety validation fails with BLOCK strategy.
 *
 * <p>This exception is thrown by {@link DruidJdbcInterceptor} when a SQL statement
 * violates safety rules and the configured {@link com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy}
 * is BLOCK.</p>
 *
 * <h2>Exception Handling</h2>
 * <p>DruidSqlSafetyFilter catches this exception and wraps it in a SQLException
 * with SQLState "42000" (syntax/access rule violation) for proper JDBC error handling.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try {
 *     connection.prepareStatement("DELETE FROM users"); // Missing WHERE
 * } catch (SQLException e) {
 *     if (e.getCause() instanceof SqlSafetyViolationException) {
 *         SqlSafetyViolationException violation = (SqlSafetyViolationException) e.getCause();
 *         ValidationResult result = violation.getValidationResult();
 *         // Handle violation...
 *     }
 * }
 * }</pre>
 *
 * @since 2.0.0
 * @see DruidJdbcInterceptor
 * @see DruidSqlSafetyFilter
 */
public class SqlSafetyViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * The validation result containing violation details.
     */
    private final ValidationResult validationResult;

    /**
     * Constructs a SqlSafetyViolationException with message and validation result.
     *
     * @param message the exception message
     * @param validationResult the validation result with violations
     */
    public SqlSafetyViolationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }

    /**
     * Constructs a SqlSafetyViolationException with message, cause, and validation result.
     *
     * @param message the exception message
     * @param cause the cause of the exception
     * @param validationResult the validation result with violations
     */
    public SqlSafetyViolationException(String message, Throwable cause, ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
    }

    /**
     * Returns the validation result containing violation details.
     *
     * @return the validation result, may be null
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * Returns the SQL state code for JDBC SQLException wrapping.
     *
     * @return "42000" (syntax/access rule violation)
     */
    public String getSqlState() {
        return "42000";
    }
}








