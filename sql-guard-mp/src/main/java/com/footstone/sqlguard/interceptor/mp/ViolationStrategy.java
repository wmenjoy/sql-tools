package com.footstone.sqlguard.interceptor.mp;

/**
 * Strategy for handling SQL validation violations at runtime.
 *
 * <p>ViolationStrategy defines how the interceptor responds when SQL validation
 * detects safety violations. The strategy determines whether execution should be
 * blocked, logged with warnings, or only logged for monitoring.</p>
 *
 * <p><strong>Strategy Behaviors:</strong></p>
 * <ul>
 *   <li><strong>BLOCK</strong> - Throws SQLException to prevent execution (production default)</li>
 *   <li><strong>WARN</strong> - Logs warning but allows execution (staging/testing)</li>
 *   <li><strong>LOG</strong> - Logs info message only (monitoring/audit mode)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * MpSqlSafetyInnerInterceptor interceptor = new MpSqlSafetyInnerInterceptor(
 *     validator,
 *     ViolationStrategy.BLOCK  // Block dangerous SQL in production
 * );
 * }</pre>
 *
 * @see MpSqlSafetyInnerInterceptor
 */
public enum ViolationStrategy {
  /**
   * Block SQL execution by throwing SQLException.
   * Use in production to enforce safety rules strictly.
   */
  BLOCK,

  /**
   * Log warning but allow SQL execution to proceed.
   * Use in staging/testing to identify issues without breaking functionality.
   */
  WARN,

  /**
   * Log info message only without warnings.
   * Use for monitoring and audit purposes in development.
   */
  LOG
}
