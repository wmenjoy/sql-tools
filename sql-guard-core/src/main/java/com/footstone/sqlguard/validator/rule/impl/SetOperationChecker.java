package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.statement.select.ExceptOp;
import net.sf.jsqlparser.statement.select.IntersectOp;
import net.sf.jsqlparser.statement.select.MinusOp;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperation;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.UnionOp;

import java.util.List;

/**
 * Rule checker that detects SQL set operations (UNION, MINUS, EXCEPT, INTERSECT).
 *
 * <p>SetOperationChecker validates SELECT statements to detect set operations that can be
 * used for SQL injection attacks, particularly data exfiltration. For example:</p>
 * <pre>{@code
 * SELECT name FROM users WHERE id = '1' UNION SELECT password FROM admin_users--
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - Set operations enable attackers to extract
 * data from arbitrary tables, bypassing application-level access controls.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses JSqlParser AST to detect SetOperationList in SELECT statements. When a SELECT
 * statement contains UNION, MINUS, EXCEPT, or INTERSECT, JSqlParser represents this as
 * a SetOperationList rather than a PlainSelect.</p>
 *
 * <p><strong>Allowlist Behavior:</strong></p>
 * <ul>
 *   <li>Empty allowedOperations list (default): blocks ALL set operations</li>
 *   <li>Populated allowedOperations: allows only specified operation types</li>
 *   <li>Case-insensitive matching for operation names</li>
 * </ul>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Simple SELECT: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>SELECT with JOIN: {@code SELECT u.* FROM users u JOIN orders o ON u.id = o.user_id}</li>
 *   <li>SELECT with subquery: {@code SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)}</li>
 *   <li>Allowed operations when configured: {@code SELECT a FROM t1 UNION SELECT b FROM t2} (if UNION in allowedOperations)</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>UNION injection: {@code SELECT name FROM users UNION SELECT password FROM admins}</li>
 *   <li>UNION ALL injection: {@code SELECT * FROM products UNION ALL SELECT * FROM secret_data}</li>
 *   <li>MINUS injection (Oracle): {@code SELECT id FROM users MINUS SELECT id FROM blocked_users}</li>
 *   <li>EXCEPT injection (PostgreSQL): {@code SELECT email FROM users EXCEPT SELECT email FROM unsubscribed}</li>
 *   <li>INTERSECT injection: {@code SELECT user_id FROM orders INTERSECT SELECT id FROM premium_users}</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Support:</strong></p>
 * <ul>
 *   <li>MySQL: UNION, UNION ALL</li>
 *   <li>Oracle: UNION, UNION ALL, MINUS, INTERSECT</li>
 *   <li>PostgreSQL: UNION, UNION ALL, EXCEPT, INTERSECT</li>
 *   <li>SQL Server: UNION, UNION ALL, EXCEPT, INTERSECT</li>
 * </ul>
 *
 * @see AbstractRuleChecker
 * @see SetOperationConfig
 * @since 1.0.0
 */
public class SetOperationChecker extends AbstractRuleChecker {

  private final SetOperationConfig config;

  /**
   * Creates a SetOperationChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public SetOperationChecker(SetOperationConfig config) {
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
   * Validates SELECT statements for set operations.
   *
   * <p>Detection algorithm:</p>
   * <ol>
   *   <li>Skip if checker is disabled</li>
   *   <li>Get SelectBody from Select statement</li>
   *   <li>Check if SelectBody is SetOperationList (indicates set operation)</li>
   *   <li>Extract operation types from SetOperationList</li>
   *   <li>Check each operation against allowedOperations list</li>
   *   <li>Add CRITICAL violation for disallowed operations</li>
   * </ol>
   *
   * @param select the SELECT statement
   * @param context the SQL execution context
   */
  @Override
  public void visitSelect(Select select, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (select == null) {
      return;
    }

    SelectBody selectBody = select.getSelectBody();
    if (selectBody == null) {
      return;
    }

    // Check if this is a set operation (UNION, MINUS, EXCEPT, INTERSECT)
    if (selectBody instanceof SetOperationList) {
      SetOperationList setOperationList = (SetOperationList) selectBody;
      checkSetOperations(setOperationList);
    }
  }

  /**
   * Checks set operations in a SetOperationList and adds violations for disallowed operations.
   *
   * @param setOperationList the set operation list to check
   */
  private void checkSetOperations(SetOperationList setOperationList) {
    List<SetOperation> operations = setOperationList.getOperations();
    if (operations == null || operations.isEmpty()) {
      return;
    }

    for (SetOperation operation : operations) {
      String operationType = getOperationType(operation);
      if (operationType != null && !config.isOperationAllowed(operationType)) {
        addViolation(
            RiskLevel.CRITICAL,
            "检测到SQL集合操作: " + operationType + " (可能存在SQL注入风险)",
            "移除集合操作或将其添加到allowedOperations配置中"
        );
      }
    }
  }

  /**
   * Extracts the operation type name from a SetOperation using instanceof checks.
   *
   * <p>JSqlParser 4.x represents set operations with specific classes:
   * UnionOp, IntersectOp, ExceptOp, MinusOp. This method uses instanceof
   * for type-safe, efficient operation type detection.</p>
   *
   * <p>For UNION operations, this method distinguishes between UNION and UNION ALL
   * using the {@link UnionOp#isAll()} method.</p>
   *
   * @param operation the set operation
   * @return the operation type name (e.g., "UNION", "UNION_ALL", "MINUS", "EXCEPT", "INTERSECT")
   */
  private String getOperationType(SetOperation operation) {
    if (operation == null) {
      return null;
    }

    // Use instanceof for type-safe checking (more efficient than string comparison)
    if (operation instanceof UnionOp) {
      UnionOp unionOp = (UnionOp) operation;
      return unionOp.isAll() ? "UNION_ALL" : "UNION";
    } else if (operation instanceof IntersectOp) {
      return "INTERSECT";
    } else if (operation instanceof ExceptOp) {
      return "EXCEPT";
    } else if (operation instanceof MinusOp) {
      return "MINUS";
    }

    // Fallback for unknown operation types (should not happen in practice)
    return operation.getClass().getSimpleName().replace("Op", "").toUpperCase();
  }
}
