package com.footstone.sqlguard.validator.rule;

import java.util.HashSet;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Abstract base class providing utility methods for SQL validation rule checkers.
 *
 * <p>AbstractRuleChecker implements the RuleChecker interface and provides protected utility
 * methods that reduce code duplication across all concrete rule checker implementations.</p>
 *
 * <p><strong>Utility Methods:</strong></p>
 * <ul>
 *   <li>{@link #extractWhere(Statement)} - Extract WHERE clause from SELECT/UPDATE/DELETE</li>
 *   <li>{@link #extractTableName(Statement)} - Extract primary table name from statement</li>
 *   <li>{@link #extractFields(Expression)} - Extract all column names from expression</li>
 *   <li>{@link #isDummyCondition(Expression)} - Detect dummy conditions like "1=1"</li>
 *   <li>{@link #isConstant(Expression)} - Identify constant values vs column references</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *   private final NoWhereClauseConfig config;
 *
 *   @Override
 *   public void check(SqlContext context, ValidationResult result) {
 *     Statement stmt = context.getParsedSql();
 *     Expression where = extractWhere(stmt);
 *     String tableName = extractTableName(stmt);
 *
 *     if (where == null && !config.getExcludedTables().contains(tableName)) {
 *       result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
 *     }
 *   }
 *
 *   @Override
 *   public boolean isEnabled() {
 *     return config.isEnabled();
 *   }
 * }
 * }</pre>
 *
 * @see RuleChecker
 * @see FieldExtractorVisitor
 */
public abstract class AbstractRuleChecker implements RuleChecker {

  /**
   * Extracts the WHERE clause expression from a SQL statement.
   *
   * <p>Supports SELECT, UPDATE, and DELETE statements. Returns null for INSERT
   * statements or statements without WHERE clauses.</p>
   *
   * <p><strong>Null-safe:</strong> Returns null for null input.</p>
   *
   * @param stmt the parsed SQL statement
   * @return the WHERE clause expression, or null if not present or not applicable
   */
  protected Expression extractWhere(Statement stmt) {
    if (stmt == null) {
      return null;
    }

    if (stmt instanceof Select) {
      Select select = (Select) stmt;
      if (select.getSelectBody() instanceof PlainSelect) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        return plainSelect.getWhere();
      }
    } else if (stmt instanceof Update) {
      Update update = (Update) stmt;
      return update.getWhere();
    } else if (stmt instanceof Delete) {
      Delete delete = (Delete) stmt;
      return delete.getWhere();
    }

    return null;
  }

  /**
   * Extracts the primary table name from a SQL statement.
   *
   * <p>For SELECT statements, extracts from the FROM clause. For UPDATE and DELETE,
   * extracts the target table. For JOIN queries, returns the primary (first) table.</p>
   *
   * <p><strong>Null-safe:</strong> Returns null for null input or unsupported statements.</p>
   *
   * @param stmt the parsed SQL statement
   * @return the table name, or null if not found
   */
  protected String extractTableName(Statement stmt) {
    if (stmt == null) {
      return null;
    }

    if (stmt instanceof Select) {
      Select select = (Select) stmt;
      if (select.getSelectBody() instanceof PlainSelect) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table) {
          return ((Table) fromItem).getName();
        }
      }
    } else if (stmt instanceof Update) {
      Update update = (Update) stmt;
      Table table = update.getTable();
      return table != null ? table.getName() : null;
    } else if (stmt instanceof Delete) {
      Delete delete = (Delete) stmt;
      Table table = delete.getTable();
      return table != null ? table.getName() : null;
    }

    return null;
  }

  /**
   * Extracts all column names from an expression using FieldExtractorVisitor.
   *
   * <p>Traverses the expression tree and collects all column references. Table prefixes
   * are automatically removed (e.g., "users.id" becomes "id").</p>
   *
   * <p><strong>Null-safe:</strong> Returns empty set for null input.</p>
   *
   * @param expr the expression to analyze
   * @return set of column names (without table prefixes)
   */
  protected Set<String> extractFields(Expression expr) {
    if (expr == null) {
      return new HashSet<>();
    }

    FieldExtractorVisitor visitor = new FieldExtractorVisitor();
    expr.accept(visitor);
    return visitor.getFields();
  }

  /**
   * Detects dummy conditions that always evaluate to true.
   *
   * <p>Recognizes common patterns:</p>
   * <ul>
   *   <li>"1=1" or "1 = 1"</li>
   *   <li>"'1'='1'"</li>
   *   <li>"true"</li>
   *   <li>Any constant numeric equality (e.g., "5=5")</li>
   * </ul>
   *
   * <p><strong>Null-safe:</strong> Returns false for null input.</p>
   *
   * @param expr the expression to check
   * @return true if the expression is a dummy condition, false otherwise
   */
  protected boolean isDummyCondition(Expression expr) {
    if (expr == null) {
      return false;
    }

    String exprStr = expr.toString().toLowerCase().replaceAll("\\s+", "");

    // Check for "1=1" pattern
    if (exprStr.equals("1=1") || exprStr.equals("'1'='1'")) {
      return true;
    }

    // Check for "true" literal
    if (exprStr.equals("true")) {
      return true;
    }

    // Check for constant equality (both sides are constants)
    if (expr instanceof EqualsTo) {
      EqualsTo equalsTo = (EqualsTo) expr;
      Expression left = equalsTo.getLeftExpression();
      Expression right = equalsTo.getRightExpression();

      if (isConstant(left) && isConstant(right)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if an expression is a constant value (literal) vs a column reference.
   *
   * <p>Constants include:</p>
   * <ul>
   *   <li>Numeric literals (LongValue, DoubleValue, etc.)</li>
   *   <li>String literals (StringValue)</li>
   *   <li>Boolean literals (true/false)</li>
   *   <li>NULL</li>
   * </ul>
   *
   * <p>Non-constants include:</p>
   * <ul>
   *   <li>Column references</li>
   *   <li>Function calls</li>
   *   <li>Subqueries</li>
   * </ul>
   *
   * <p><strong>Null-safe:</strong> Returns false for null input.</p>
   *
   * @param expr the expression to check
   * @return true if the expression is a constant, false if it's a column or complex expression
   */
  protected boolean isConstant(Expression expr) {
    if (expr == null) {
      return false;
    }

    // Column references are not constants
    if (expr instanceof Column) {
      return false;
    }

    // Check for common constant types by class name
    String className = expr.getClass().getSimpleName();
    return className.contains("Value") || className.equals("NullValue");
  }

  /**
   * Visitor that extracts all column names from an expression tree.
   *
   * <p>This inner class extends JSqlParser's ExpressionVisitorAdapter to traverse
   * the expression AST and collect all column references. Table prefixes are
   * automatically removed to provide clean field names.</p>
   *
   * <p><strong>Usage:</strong></p>
   * <pre>{@code
   * FieldExtractorVisitor visitor = new FieldExtractorVisitor();
   * expression.accept(visitor);
   * Set<String> fields = visitor.getFields();
   * }</pre>
   */
  protected static class FieldExtractorVisitor extends ExpressionVisitorAdapter {

    private final Set<String> fields = new HashSet<>();

    /**
     * Returns the set of extracted field names.
     *
     * @return set of column names (without table prefixes)
     */
    public Set<String> getFields() {
      return fields;
    }

    /**
     * Visits a column node and extracts its name.
     *
     * <p>Removes table prefixes (e.g., "users.id" becomes "id").</p>
     *
     * @param column the column node to visit
     */
    @Override
    public void visit(Column column) {
      String columnName = column.getColumnName();
      if (columnName != null) {
        // Remove table prefix if present (e.g., "users.id" -> "id")
        int dotIndex = columnName.lastIndexOf('.');
        if (dotIndex >= 0) {
          columnName = columnName.substring(dotIndex + 1);
        }
        fields.add(columnName);
      }
    }
  }
}








