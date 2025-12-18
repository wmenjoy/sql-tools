package com.footstone.sqlguard.interceptor.hikari;

/**
 * Strategy for handling SQL validation violations at runtime in HikariCP interceptor.
 *
 * <p>ViolationStrategy defines how the HikariCP proxy responds when SQL validation
 * violations are detected. This allows flexible deployment strategies from observation-only
 * to strict enforcement.</p>
 *
 * <p><strong>Strategy Behaviors:</strong></p>
 * <ul>
 *   <li><strong>BLOCK</strong> - Throw SQLException, prevent SQL execution (fail-closed)</li>
 *   <li><strong>WARN</strong> - Log error level message, continue execution (fail-open)</li>
 *   <li><strong>LOG</strong> - Log warning level message, continue execution (observation mode)</li>
 * </ul>
 *
 * <p><strong>Deployment Recommendations:</strong></p>
 * <ul>
 *   <li><strong>Development</strong>: BLOCK - catch issues early</li>
 *   <li><strong>Staging</strong>: WARN - identify violations without blocking</li>
 *   <li><strong>Production (initial rollout)</strong>: LOG - observe without impact</li>
 *   <li><strong>Production (mature)</strong>: BLOCK or WARN - enforce safety rules</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * HikariConfig config = new HikariConfig();
 * config.setProxyFactory(new HikariSqlSafetyProxyFactory(
 *     validator,
 *     ViolationStrategy.WARN  // Log errors but continue execution
 * ));
 * }</pre>
 *
 * @see HikariSqlSafetyProxyFactory
 */
public enum ViolationStrategy {
  /**
   * Block SQL execution by throwing SQLException.
   *
   * <p>When violations are detected:</p>
   * <ul>
   *   <li>Log error message with violation details</li>
   *   <li>Throw SQLException with SQLState "42000" (syntax/access rule violation)</li>
   *   <li>SQL execution is prevented</li>
   *   <li>Transaction is rolled back (if active)</li>
   * </ul>
   *
   * <p><strong>Use when:</strong> Zero tolerance for SQL safety violations</p>
   */
  BLOCK,

  /**
   * Log error and continue SQL execution.
   *
   * <p>When violations are detected:</p>
   * <ul>
   *   <li>Log error message with violation details</li>
   *   <li>SQL execution continues normally</li>
   *   <li>No exception thrown</li>
   *   <li>Suitable for gradual rollout</li>
   * </ul>
   *
   * <p><strong>Use when:</strong> Need visibility but can't block production traffic</p>
   */
  WARN,

  /**
   * Log warning and continue SQL execution.
   *
   * <p>When violations are detected:</p>
   * <ul>
   *   <li>Log warning message with violation details</li>
   *   <li>SQL execution continues normally</li>
   *   <li>No exception thrown</li>
   *   <li>Minimal logging noise</li>
   * </ul>
   *
   * <p><strong>Use when:</strong> Observation mode, collecting metrics</p>
   */
  LOG
}




