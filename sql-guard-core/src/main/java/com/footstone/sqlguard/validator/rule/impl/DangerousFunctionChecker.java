package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule checker that detects dangerous database functions that enable file operations,
 * OS command execution, and DoS attacks.
 *
 * <p>DangerousFunctionChecker validates SQL statements to detect dangerous functions such as:</p>
 * <ul>
 *   <li><strong>load_file:</strong> Arbitrary file read from server filesystem</li>
 *   <li><strong>sys_exec/sys_eval:</strong> OS command execution (MySQL UDF)</li>
 *   <li><strong>sleep/benchmark:</strong> DoS attacks via query delay</li>
 *   <li><strong>into_outfile/into_dumpfile:</strong> File write operations</li>
 * </ul>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses recursive AST traversal via ExpressionVisitorAdapter to find all Function objects
 * in the SQL statement, regardless of their location (SELECT, WHERE, HAVING, ORDER BY, etc.).</p>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - Dangerous functions enable attackers to:</p>
 * <ul>
 *   <li>Read arbitrary files from the server</li>
 *   <li>Execute operating system commands</li>
 *   <li>Cause denial of service via delays</li>
 *   <li>Exfiltrate data via time-based attacks</li>
 * </ul>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Aggregate functions: {@code SELECT MAX(id), COUNT(*) FROM users}</li>
 *   <li>String functions: {@code SELECT CONCAT(first_name, ' ', last_name) FROM users}</li>
 *   <li>Date functions: {@code SELECT NOW(), DATE_FORMAT(created_at, '%Y-%m-%d')}</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>File read: {@code SELECT load_file('/etc/passwd')}</li>
 *   <li>DoS attack: {@code SELECT * FROM users WHERE sleep(5) = 0}</li>
 *   <li>Nested function: {@code SELECT CONCAT(load_file('/etc/passwd'), 'x')}</li>
 *   <li>Subquery: {@code WHERE id IN (SELECT load_file('/etc/passwd'))}</li>
 * </ul>
 *
 * @see AbstractRuleChecker
 * @see DangerousFunctionConfig
 * @since 1.0.0
 */
public class DangerousFunctionChecker extends AbstractRuleChecker {

  private final DangerousFunctionConfig config;

  /**
   * Creates a DangerousFunctionChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public DangerousFunctionChecker(DangerousFunctionConfig config) {
    super(config);
    this.config = config;
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

  /**
   * Validates SELECT statements for dangerous functions.
   *
   * <p>Extracts all expressions from SELECT clause, WHERE clause, HAVING clause,
   * and ORDER BY clause, then uses recursive visitor to find all Function objects.</p>
   *
   * @param select  the SELECT statement
   * @param context the SQL execution context
   */
  @Override
  public void visitSelect(Select select, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (select == null || select.getSelectBody() == null) {
      return;
    }

    // Collect all expressions from SELECT statement
    List<Expression> expressions = new ArrayList<>();

    if (select.getSelectBody() instanceof PlainSelect) {
      PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

      // Extract from SELECT clause
      if (plainSelect.getSelectItems() != null) {
        for (SelectItem item : plainSelect.getSelectItems()) {
          if (item instanceof SelectExpressionItem) {
            expressions.add(((SelectExpressionItem) item).getExpression());
          }
        }
      }

      // Extract from WHERE clause
      if (plainSelect.getWhere() != null) {
        expressions.add(plainSelect.getWhere());
      }

      // Extract from HAVING clause
      if (plainSelect.getHaving() != null) {
        expressions.add(plainSelect.getHaving());
      }

      // Extract from ORDER BY clause
      if (plainSelect.getOrderByElements() != null) {
        for (OrderByElement orderBy : plainSelect.getOrderByElements()) {
          if (orderBy.getExpression() != null) {
            expressions.add(orderBy.getExpression());
          }
        }
      }
    }

    // Check all expressions for dangerous functions
    checkExpressionsForDangerousFunctions(expressions);
  }

  /**
   * Validates UPDATE statements for dangerous functions.
   *
   * <p>Extracts all expressions from SET clause and WHERE clause,
   * then uses recursive visitor to find all Function objects.</p>
   *
   * @param update  the UPDATE statement
   * @param context the SQL execution context
   */
  @Override
  public void visitUpdate(Update update, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (update == null) {
      return;
    }

    // Collect all expressions from UPDATE statement
    List<Expression> expressions = new ArrayList<>();

    // Extract from SET clause
    if (update.getUpdateSets() != null) {
      for (UpdateSet updateSet : update.getUpdateSets()) {
        if (updateSet.getExpressions() != null) {
          expressions.addAll(updateSet.getExpressions());
        }
      }
    }

    // Extract from WHERE clause
    if (update.getWhere() != null) {
      expressions.add(update.getWhere());
    }

    // Check all expressions for dangerous functions
    checkExpressionsForDangerousFunctions(expressions);
  }

  /**
   * Validates DELETE statements for dangerous functions.
   *
   * <p>Extracts expression from WHERE clause,
   * then uses recursive visitor to find all Function objects.</p>
   *
   * @param delete  the DELETE statement
   * @param context the SQL execution context
   */
  @Override
  public void visitDelete(Delete delete, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (delete == null) {
      return;
    }

    // Collect all expressions from DELETE statement
    List<Expression> expressions = new ArrayList<>();

    // Extract from WHERE clause
    if (delete.getWhere() != null) {
      expressions.add(delete.getWhere());
    }

    // Check all expressions for dangerous functions
    checkExpressionsForDangerousFunctions(expressions);
  }

  /**
   * Validates INSERT statements for dangerous functions.
   *
   * <p>Extracts expressions from VALUES clause,
   * then uses recursive visitor to find all Function objects.</p>
   *
   * @param insert  the INSERT statement
   * @param context the SQL execution context
   */
  @Override
  public void visitInsert(Insert insert, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (insert == null) {
      return;
    }

    // Collect all expressions from INSERT statement
    List<Expression> expressions = new ArrayList<>();

    // Extract from VALUES clause
    // Note: getItemsList() is deprecated in newer JSqlParser versions but still works in 4.6
    @SuppressWarnings("deprecation")
    net.sf.jsqlparser.expression.operators.relational.ItemsList itemsList = insert.getItemsList();
    if (itemsList instanceof net.sf.jsqlparser.expression.operators.relational.ExpressionList) {
      net.sf.jsqlparser.expression.operators.relational.ExpressionList exprList = 
          (net.sf.jsqlparser.expression.operators.relational.ExpressionList) itemsList;
      if (exprList.getExpressions() != null) {
        expressions.addAll(exprList.getExpressions());
      }
    }

    // Extract from SELECT (INSERT ... SELECT)
    if (insert.getSelect() != null) {
      // Recursively check SELECT in INSERT
      visitSelect(insert.getSelect(), getCurrentContext());
    }

    // Check all expressions for dangerous functions
    checkExpressionsForDangerousFunctions(expressions);
  }

  /**
   * Checks a list of expressions for dangerous functions.
   *
   * @param expressions the list of expressions to check
   */
  private void checkExpressionsForDangerousFunctions(List<Expression> expressions) {
    if (expressions == null || expressions.isEmpty()) {
      return;
    }

    // Use visitor to extract all function names
    FunctionExtractorVisitor visitor = new FunctionExtractorVisitor();
    for (Expression expr : expressions) {
      if (expr != null) {
        expr.accept(visitor);
      }
    }

    // Get all extracted function names
    Set<String> foundFunctions = visitor.getFunctionNames();

    // Check for dangerous functions
    List<String> deniedFunctions = config.getDeniedFunctions();
    if (deniedFunctions == null || deniedFunctions.isEmpty()) {
      return; // No denied functions configured, allow all
    }

    // Find matches (case-insensitive)
    List<String> detectedDangerous = foundFunctions.stream()
        .filter(funcName -> deniedFunctions.contains(funcName.toLowerCase()))
        .collect(Collectors.toList());

    // Add violation for each detected dangerous function
    for (String dangerousFunc : detectedDangerous) {
      String message = String.format(
          "检测到危险函数: %s (可能存在安全风险)",
          dangerousFunc
      );
      String suggestion = String.format(
          "移除危险函数 %s，使用安全的替代方案",
          dangerousFunc
      );
      addViolation(RiskLevel.CRITICAL, message, suggestion);
    }
  }

  /**
   * Visitor that recursively extracts all Function names from an Expression tree.
   *
   * <p>Uses IdentityHashMap-based visited set to prevent infinite loops on
   * circular references while traversing the AST.</p>
   *
   * <p><strong>Traversal Coverage:</strong></p>
   * <ul>
   *   <li>Direct Function calls</li>
   *   <li>Functions nested in other functions (as arguments)</li>
   *   <li>Functions in SubSelect expressions</li>
   *   <li>Functions in CASE WHEN expressions</li>
   *   <li>Functions in binary expressions (AND, OR, comparisons)</li>
   * </ul>
   */
  private static class FunctionExtractorVisitor extends ExpressionVisitorAdapter {

    /**
     * Set of collected function names (lowercase for case-insensitive comparison).
     */
    private final Set<String> functionNames = new HashSet<>();

    /**
     * Visited expressions set to prevent infinite recursion.
     * Uses IdentityHashMap for reference-based comparison (faster than equals).
     */
    private final Set<Expression> visitedSet = java.util.Collections.newSetFromMap(
        new IdentityHashMap<>()
    );

    /**
     * Returns the collected function names.
     *
     * @return set of function names (lowercase)
     */
    public Set<String> getFunctionNames() {
      return functionNames;
    }

    /**
     * Visits a Function expression and extracts its name.
     * Also recursively visits function arguments to find nested functions.
     *
     * @param function the Function expression
     */
    @Override
    public void visit(Function function) {
      if (function == null || !markVisited(function)) {
        return;
      }

      // Extract function name
      String funcName = function.getName();
      if (funcName != null && !funcName.trim().isEmpty()) {
        functionNames.add(funcName.toLowerCase());
      }

      // Recursively visit function arguments (for nested functions)
      if (function.getParameters() != null && function.getParameters().getExpressions() != null) {
        for (Expression arg : function.getParameters().getExpressions()) {
          if (arg != null) {
            arg.accept(this);
          }
        }
      }
    }

    /**
     * Visits a SubSelect expression and recursively extracts functions from it.
     *
     * @param subSelect the SubSelect expression
     */
    @Override
    public void visit(SubSelect subSelect) {
      if (subSelect == null || !markVisited(subSelect)) {
        return;
      }

      // Recursively process subquery
      if (subSelect.getSelectBody() instanceof PlainSelect) {
        PlainSelect plainSelect = (PlainSelect) subSelect.getSelectBody();

        // Extract from SELECT clause
        if (plainSelect.getSelectItems() != null) {
          for (SelectItem item : plainSelect.getSelectItems()) {
            if (item instanceof SelectExpressionItem) {
              Expression expr = ((SelectExpressionItem) item).getExpression();
              if (expr != null) {
                expr.accept(this);
              }
            }
          }
        }

        // Extract from WHERE clause
        if (plainSelect.getWhere() != null) {
          plainSelect.getWhere().accept(this);
        }

        // Extract from HAVING clause
        if (plainSelect.getHaving() != null) {
          plainSelect.getHaving().accept(this);
        }
      }
    }

    /**
     * Visits a CaseExpression and recursively extracts functions from WHEN/THEN/ELSE clauses.
     *
     * @param caseExpression the CaseExpression
     */
    @Override
    public void visit(CaseExpression caseExpression) {
      if (caseExpression == null || !markVisited(caseExpression)) {
        return;
      }

      // Visit switch expression (if present)
      if (caseExpression.getSwitchExpression() != null) {
        caseExpression.getSwitchExpression().accept(this);
      }

      // Visit WHEN clauses
      if (caseExpression.getWhenClauses() != null) {
        for (WhenClause whenClause : caseExpression.getWhenClauses()) {
          if (whenClause.getWhenExpression() != null) {
            whenClause.getWhenExpression().accept(this);
          }
          if (whenClause.getThenExpression() != null) {
            whenClause.getThenExpression().accept(this);
          }
        }
      }

      // Visit ELSE expression
      if (caseExpression.getElseExpression() != null) {
        caseExpression.getElseExpression().accept(this);
      }
    }

    /**
     * Marks an expression as visited to prevent infinite recursion.
     *
     * @param expr the expression to mark
     * @return true if not previously visited, false if already visited
     */
    private boolean markVisited(Expression expr) {
      if (expr == null) {
        return false;
      }
      return visitedSet.add(expr);
    }
  }
}
