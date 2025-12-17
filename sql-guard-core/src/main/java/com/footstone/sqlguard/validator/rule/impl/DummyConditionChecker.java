package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;

/**
 * Checker that detects invalid/dummy WHERE conditions.
 *
 * <p>Identifies meaningless WHERE conditions like "1=1", "true", "'a'='a'" that developers
 * often add for dynamic SQL convenience but effectively make the WHERE clause useless,
 * resulting in full-table scans despite apparent WHERE clause presence.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <ul>
 *   <li><strong>Pattern-based:</strong> String matching against known dummy patterns
 *       (handles spacing variations via normalization)</li>
 *   <li><strong>AST-based:</strong> Uses {@link AbstractRuleChecker#isDummyCondition(Expression)}
 *       to detect programmatically generated constant comparisons</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> HIGH - Bypasses NoWhereClauseChecker but still causes
 * full-table scans, potentially affecting large datasets.</p>
 *
 * <p><strong>Common Patterns Detected:</strong></p>
 * <ul>
 *   <li>WHERE 1=1 (with or without spaces)</li>
 *   <li>WHERE '1'='1' or 'a'='a'</li>
 *   <li>WHERE true</li>
 *   <li>WHERE 2=2, 100=100 (any constant equality)</li>
 *   <li>WHERE status='active' AND 1=1 (embedded dummy conditions)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * DummyConditionConfig config = new DummyConditionConfig();
 * config.setCustomPatterns(Arrays.asList("0=0")); // Add organization-specific patterns
 * DummyConditionChecker checker = new DummyConditionChecker(config);
 *
 * SqlContext context = new SqlContext(sql, parsedStatement);
 * ValidationResult result = new ValidationResult();
 * checker.check(context, result);
 * }</pre>
 *
 * @see AbstractRuleChecker#isDummyCondition(Expression)
 * @see DummyConditionConfig
 */
public class DummyConditionChecker extends AbstractRuleChecker {

  private final DummyConditionConfig config;

  /**
   * Constructs a DummyConditionChecker with the specified configuration.
   *
   * @param config the configuration containing pattern lists and enabled flag
   */
  public DummyConditionChecker(DummyConditionConfig config) {
    this.config = config;
  }

  /**
   * Checks for dummy conditions in the WHERE clause using dual detection.
   *
   * <p><strong>Detection Process:</strong></p>
   * <ol>
   *   <li>Extract WHERE clause using {@link #extractWhere(Statement)}</li>
   *   <li>If no WHERE clause, return (nothing to check)</li>
   *   <li>Pattern-based detection: Normalize WHERE string and check against patterns</li>
   *   <li>AST-based detection: Use {@link #isDummyCondition(Expression)} for constant equality</li>
   *   <li>If either method detects dummy condition, add HIGH violation</li>
   * </ol>
   *
   * @param context the SQL context containing parsed statement
   * @param result the validation result to populate with violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    // Skip check if disabled
    if (!isEnabled()) {
      return;
    }

    Statement stmt = context.getParsedSql();
    Expression where = extractWhere(stmt);

    // No WHERE clause means nothing to check
    if (where == null) {
      return;
    }

    boolean isDummy = false;

    // Pattern-based detection
    String normalizedWhere = where.toString().toLowerCase().replaceAll("\\s+", " ");
    for (String pattern : config.getPatterns()) {
      String normalizedPattern = pattern.toLowerCase().replaceAll("\\s+", " ");
      if (normalizedWhere.contains(normalizedPattern)) {
        isDummy = true;
        break;
      }
    }

    // Check custom patterns if not already detected
    if (!isDummy) {
      for (String pattern : config.getCustomPatterns()) {
        String normalizedPattern = pattern.toLowerCase().replaceAll("\\s+", " ");
        if (normalizedWhere.contains(normalizedPattern)) {
          isDummy = true;
          break;
        }
      }
    }

    // AST-based detection (catches programmatically generated constant comparisons)
    if (!isDummy && isDummyCondition(where)) {
      isDummy = true;
    }

    // Add violation if dummy condition detected
    if (isDummy) {
      result.addViolation(
          RiskLevel.HIGH,
          "检测到无效条件(如 1=1),请移除",
          "使用<where>标签或真实业务条件"
      );
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





