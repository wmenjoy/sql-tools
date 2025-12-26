package com.footstone.sqlguard.config;

/**
 * Enumeration defining how SQL Guard handles detected violations.
 *
 * <h2>Strategy Options</h2>
 * <ul>
 *   <li><b>BLOCK</b>: Throw {@link java.sql.SQLException} to prevent SQL execution.
 *       Use in production when data integrity is paramount.</li>
 *   <li><b>WARN</b>: Log violations at WARN level but allow execution to proceed.
 *       Use during migration phases or for non-critical rules.</li>
 *   <li><b>LOG</b>: Log violations at INFO level for monitoring purposes.
 *       Use for auditing or gathering metrics without impacting execution.</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * sql-guard:
 *   violation-strategy: BLOCK  # or WARN, LOG
 * }</pre>
 *
 * <h2>Runtime Behavior</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────┐
 * │ ViolationStrategy Decision Flow                 │
 * ├─────────────────────────────────────────────────┤
 * │ Violation Detected                              │
 * │         ↓                                       │
 * │ Strategy = BLOCK?                               │
 * │     YES → throw SQLException (SQL not executed) │
 * │     NO ↓                                        │
 * │ Strategy = WARN?                                │
 * │     YES → log.warn() → continue execution       │
 * │     NO ↓                                        │
 * │ Strategy = LOG?                                 │
 * │     YES → log.info() → continue execution       │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * @since 1.1.0
 * @see com.footstone.sqlguard.interceptor.inner.impl.SqlGuardCheckInnerInterceptor
 */
public enum ViolationStrategy {

    /**
     * Block SQL execution by throwing SQLException.
     *
     * <p>Use when violations represent critical security or data integrity risks
     * that must prevent the query from executing. This is the safest option
     * but may cause application errors if not handled properly.</p>
     */
    BLOCK,

    /**
     * Log violation at WARN level and continue execution.
     *
     * <p>Use during gradual migration phases when you want visibility into
     * violations without impacting application functionality. Allows teams
     * to address issues incrementally.</p>
     */
    WARN,

    /**
     * Log violation at INFO level and continue execution.
     *
     * <p>Use for monitoring and auditing purposes. Useful for gathering
     * metrics about SQL patterns without any impact on application behavior.
     * Least intrusive option.</p>
     */
    LOG
}







