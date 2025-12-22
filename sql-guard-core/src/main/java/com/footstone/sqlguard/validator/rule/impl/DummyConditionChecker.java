package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

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
    super(config);  // NEW: Pass config to AbstractRuleChecker
    this.config = config;
  }

  /**
   * Visit SELECT statement to check for dummy conditions.
   *
   * @param select the SELECT statement
   * @param context the SQL context
   */
  @Override
  public void visitSelect(Select select, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (select.getSelectBody() instanceof PlainSelect) {
      PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
      Expression where = plainSelect.getWhere();
      validateDummyCondition(where);
    }
  }

  /**
   * Visit UPDATE statement to check for dummy conditions.
   *
   * @param update the UPDATE statement
   * @param context the SQL context
   */
  @Override
  public void visitUpdate(Update update, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    Expression where = update.getWhere();
    validateDummyCondition(where);
  }

  /**
   * Visit DELETE statement to check for dummy conditions.
   *
   * @param delete the DELETE statement
   * @param context the SQL context
   */
  @Override
  public void visitDelete(Delete delete, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    Expression where = delete.getWhere();
    validateDummyCondition(where);
  }

  /**
   * Validates WHERE expression for dummy conditions.
   *
   * <p><strong>Detection Process:</strong></p>
   * <ol>
   *   <li>If no WHERE clause, return (nothing to check)</li>
   *   <li>Pattern-based detection: Normalize WHERE string and check against patterns</li>
   *   <li>AST-based detection: Use local isDummyConditionExpression() for constant equality</li>
   *   <li>If either method detects dummy condition, add HIGH violation</li>
   * </ol>
   *
   * @param where the WHERE expression to validate
   */
  private void validateDummyCondition(Expression where) {
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
    if (!isDummy && isDummyConditionExpression(where)) {
      isDummy = true;
    }

    // Add violation if dummy condition detected
    if (isDummy) {
      addViolation(
          RiskLevel.HIGH,
          "检测到无效条件(如 1=1),请移除"
      );
    }
  }

  /**
   * Checks if expression is a dummy condition using AST analysis.
   * Replaces the removed AbstractRuleChecker.isDummyCondition() method.
   *
   * @param where the WHERE expression to check
   * @return true if this is a dummy condition
   */
  private boolean isDummyConditionExpression(Expression where) {
    if (where == null) {
      return false;
    }

    // String-based patterns
    String whereStr = where.toString().trim().toUpperCase();
    if (whereStr.equals("1=1") || whereStr.equals("1 = 1") ||
        whereStr.equals("TRUE") || whereStr.equals("'1'='1'")) {
      return true;
    }

    // AST-based: Check for constant equality (e.g., 1=1, 'a'='a')
    if (where instanceof EqualsTo) {
      EqualsTo equals = (EqualsTo) where;
      Expression left = equals.getLeftExpression();
      Expression right = equals.getRightExpression();

      // Both sides are constants
      if (isConstant(left) && isConstant(right)) {
        // Same constant value (e.g., 1=1, 'a'='a')
        if (left.toString().equals(right.toString())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if expression is a constant value.
   *
   * @param expr the expression to check
   * @return true if this is a constant value
   */
  private boolean isConstant(Expression expr) {
    return expr instanceof LongValue || expr instanceof StringValue;
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













