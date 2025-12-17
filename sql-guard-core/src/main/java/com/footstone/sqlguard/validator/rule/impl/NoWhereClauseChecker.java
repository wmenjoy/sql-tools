package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Rule checker that detects SQL statements missing WHERE clauses.
 *
 * <p>NoWhereClauseChecker validates SELECT, UPDATE, and DELETE statements to ensure they include
 * WHERE clauses. Missing WHERE clauses can cause catastrophic consequences:</p>
 *
 * <ul>
 *   <li><strong>DELETE without WHERE:</strong> Irreversible deletion of all table data</li>
 *   <li><strong>UPDATE without WHERE:</strong> Irreversible modification of all table rows</li>
 *   <li><strong>SELECT without WHERE:</strong> Memory exhaustion from loading entire tables</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - This is the most severe validation check</p>
 *
 * <p><strong>Edge Cases:</strong></p>
 * <ul>
 *   <li>INSERT statements are skipped (no WHERE clause by design)</li>
 *   <li>Statements with dummy conditions like "WHERE 1=1" pass this check
 *       (handled by DummyConditionChecker)</li>
 *   <li>Complex WHERE clauses always pass regardless of effectiveness</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * NoWhereClauseConfig config = new NoWhereClauseConfig();
 * NoWhereClauseChecker checker = new NoWhereClauseChecker(config);
 *
 * SqlContext context = parser.parse("DELETE FROM users");
 * ValidationResult result = new ValidationResult();
 * checker.check(context, result);
 *
 * // Result contains CRITICAL violation
 * }</pre>
 *
 * @see AbstractRuleChecker
 * @see NoWhereClauseConfig
 */
public class NoWhereClauseChecker extends AbstractRuleChecker {

  private final NoWhereClauseConfig config;

  /**
   * Creates a NoWhereClauseChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public NoWhereClauseChecker(NoWhereClauseConfig config) {
    this.config = config;
  }

  /**
   * Checks if the SQL statement is missing a WHERE clause.
   *
   * <p>Validation logic:</p>
   * <ol>
   *   <li>Skip check if checker is disabled</li>
   *   <li>Skip INSERT statements (no WHERE clause by design)</li>
   *   <li>Extract WHERE clause using AbstractRuleChecker utility</li>
   *   <li>If WHERE is null for SELECT/UPDATE/DELETE, add CRITICAL violation</li>
   * </ol>
   *
   * @param context the SQL context containing parsed statement
   * @param result the validation result to populate with violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (!isEnabled()) {
      return;
    }

    Statement statement = context.getParsedSql();

    // Skip INSERT statements - they don't have WHERE clauses by design
    if (statement instanceof Insert) {
      return;
    }

    // Only check SELECT, UPDATE, DELETE statements
    if (!(statement instanceof Select
        || statement instanceof Update
        || statement instanceof Delete)) {
      return;
    }

    // Extract WHERE clause using AbstractRuleChecker utility
    Expression where = extractWhere(statement);

    // If WHERE clause is missing, add CRITICAL violation
    if (where == null) {
      result.addViolation(
          RiskLevel.CRITICAL,
          "SQL语句缺少WHERE条件,可能导致全表操作",
          "请添加WHERE条件限制操作范围");
    }
  }

  /**
   * Returns whether this checker is enabled.
   *
   * @return true if enabled, false otherwise
   */
  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }
}







