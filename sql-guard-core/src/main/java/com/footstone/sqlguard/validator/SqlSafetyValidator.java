package com.footstone.sqlguard.validator;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;

/**
 * Public interface for SQL safety validation operations.
 *
 * <p>SqlSafetyValidator provides the primary contract for validating SQL statements
 * against configured safety rules. This interface is used by interceptors in Phase 4
 * (MyBatis, JDBC, MyBatis-Plus integrations) to perform validation before SQL execution.</p>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Single responsibility: validate SQL statements and return results</li>
 *   <li>Stateless: implementations should be thread-safe and reusable</li>
 *   <li>Non-blocking: validation should complete quickly (target <5% overhead)</li>
 *   <li>Comprehensive: aggregates violations from all enabled rule checkers</li>
 * </ul>
 *
 * <p><strong>Usage Example (Phase 4 Interceptor):</strong></p>
 * <pre>{@code
 * public class MyBatisInterceptor implements Interceptor {
 *   private final SqlSafetyValidator validator;
 *
 *   @Override
 *   public Object intercept(Invocation invocation) throws Throwable {
 *     SqlContext context = buildContext(invocation);
 *     ValidationResult result = validator.validate(context);
 *
 *     if (!result.isPassed()) {
 *       // Handle violations based on risk level
 *       if (result.getRiskLevel() == RiskLevel.CRITICAL) {
 *         throw new SqlValidationException(result);
 *       } else {
 *         log.warn("SQL validation warnings: {}", result.getViolations());
 *       }
 *     }
 *
 *     return invocation.proceed();
 *   }
 * }
 * }</pre>
 *
 * @see DefaultSqlSafetyValidator
 * @see SqlContext
 * @see ValidationResult
 */
public interface SqlSafetyValidator {

  /**
   * Validates a SQL statement against all enabled safety rules.
   *
   * <p>This method performs comprehensive validation by:</p>
   * <ul>
   *   <li>Checking SQL deduplication filter to avoid redundant validation</li>
   *   <li>Parsing SQL statement (if not already parsed) via JSqlParser facade</li>
   *   <li>Executing all enabled rule checkers via orchestrator</li>
   *   <li>Aggregating violations to highest risk level in result</li>
   * </ul>
   *
   * <p><strong>Performance Characteristics:</strong></p>
   * <ul>
   *   <li>Parse-once optimization: SQL parsed only once, AST reused by all checkers</li>
   *   <li>Deduplication: Same SQL within TTL window returns cached pass result</li>
   *   <li>Target overhead: <5% compared to SQL execution time</li>
   * </ul>
   *
   * <p><strong>Thread Safety:</strong> This method must be thread-safe. Implementations
   * should avoid shared mutable state or use proper synchronization.</p>
   *
   * <p><strong>Error Handling:</strong></p>
   * <ul>
   *   <li>Fail-fast mode: Parse errors throw SqlParseException</li>
   *   <li>Lenient mode: Parse errors logged and return pass result</li>
   * </ul>
   *
   * @param context the SQL execution context containing statement and metadata
   * @return ValidationResult containing violations and overall risk level; never null
   * @throws IllegalArgumentException if context is null or invalid
   * @throws com.footstone.sqlguard.parser.SqlParseException if parsing fails in fail-fast mode
   */
  ValidationResult validate(SqlContext context);
}




