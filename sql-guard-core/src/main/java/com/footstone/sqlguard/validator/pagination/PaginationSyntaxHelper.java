package com.footstone.sqlguard.validator.pagination;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * Utility class for pagination syntax detection and parameter extraction.
 *
 * <p>Centralizes pagination logic to avoid code duplication across checkers.
 * Supports multiple database dialects including MySQL, PostgreSQL, SQL Server,
 * Oracle, and DB2.</p>
 *
 * <h2>Supported Pagination Syntaxes</h2>
 * <ul>
 *   <li><strong>MySQL/PostgreSQL:</strong> LIMIT n [OFFSET m]</li>
 *   <li><strong>MySQL:</strong> LIMIT m, n (comma syntax)</li>
 *   <li><strong>SQL Server:</strong> TOP n</li>
 *   <li><strong>SQL Server 2012+:</strong> OFFSET m ROWS FETCH NEXT n ROWS ONLY</li>
 *   <li><strong>DB2/Oracle 12c+:</strong> FETCH FIRST n ROWS ONLY</li>
 *   <li><strong>Oracle (legacy):</strong> ROWNUM</li>
 * </ul>
 *
 * <h2>Detection Strategy</h2>
 * <p>Two-layer detection for optimal performance:</p>
 * <ul>
 *   <li><strong>Layer 1 (AST-based):</strong> O(1) field access for outer-level pagination</li>
 *   <li><strong>Layer 2 (String-based):</strong> O(n) regex/contains for nested queries and UNION</li>
 * </ul>
 *
 * @since 1.2.0
 */
public final class PaginationSyntaxHelper {

  private PaginationSyntaxHelper() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  // ==================== Pagination Detection ====================

  /**
   * AST-based detection for pagination clauses.
   *
   * <p>Checks if the PlainSelect has any pagination clause (LIMIT, TOP, or FETCH).
   * This is O(1) field access and handles outer-level pagination.</p>
   *
   * @param plainSelect the PlainSelect statement to check (nullable)
   * @return true if any pagination clause is present, false otherwise
   */
  public static boolean hasPaginationClause(PlainSelect plainSelect) {
    if (plainSelect == null) {
      return false;
    }
    return plainSelect.getLimit() != null
        || plainSelect.getTop() != null
        || plainSelect.getFetch() != null;
  }

  /**
   * SQL string-based detection for pagination keywords.
   *
   * <p>Checks if the SQL string contains any pagination keywords. This is O(n)
   * and handles nested queries, UNION statements, and other complex cases that
   * AST-based detection might miss.</p>
   *
   * <p>Detected keywords:</p>
   * <ul>
   *   <li>LIMIT (MySQL/PostgreSQL)</li>
   *   <li>TOP (SQL Server)</li>
   *   <li>FETCH FIRST/NEXT (DB2/Oracle 12c+)</li>
   *   <li>ROWNUM (Oracle legacy)</li>
   *   <li>ROW_NUMBER (window function pagination)</li>
   * </ul>
   *
   * @param sql the SQL string to check (nullable)
   * @return true if any pagination keyword is found, false otherwise
   */
  public static boolean hasPaginationKeyword(String sql) {
    if (sql == null) {
      return false;
    }
    String upper = sql.toUpperCase();

    // MySQL/PostgreSQL: LIMIT clause with number
    if (upper.matches(".*\\bLIMIT\\s+\\d+.*")) {
      return true;
    }

    // SQL Server: TOP clause (both "TOP 100" and "TOP(100)" syntax)
    if (upper.matches(".*\\bTOP\\s*\\(?\\d+.*")) {
      return true;
    }

    // DB2/Oracle 12c+: FETCH FIRST/NEXT clause
    if (upper.contains("FETCH FIRST") || upper.contains("FETCH NEXT")) {
      return true;
    }

    // Oracle legacy: ROWNUM or ROW_NUMBER window function
    if (upper.contains("ROWNUM") || upper.contains("ROW_NUMBER")) {
      return true;
    }

    return false;
  }

  // ==================== Parameter Extraction ====================

  /**
   * Extracts pageSize (row count) from pagination clause.
   *
   * <p>Supports extraction from:</p>
   * <ul>
   *   <li>LIMIT clause (MySQL/PostgreSQL)</li>
   *   <li>TOP clause (SQL Server)</li>
   *   <li>FETCH clause (DB2/Oracle 12c+)</li>
   * </ul>
   *
   * @param plainSelect the PlainSelect statement (nullable)
   * @return pageSize value, or null if not found or not a numeric literal
   */
  public static Long extractPageSize(PlainSelect plainSelect) {
    if (plainSelect == null) {
      return null;
    }

    // Priority 1: LIMIT clause (MySQL/PostgreSQL)
    if (plainSelect.getLimit() != null && plainSelect.getLimit().getRowCount() != null) {
      return parseExpression(plainSelect.getLimit().getRowCount());
    }

    // Priority 2: TOP clause (SQL Server)
    if (plainSelect.getTop() != null && plainSelect.getTop().getExpression() != null) {
      return parseExpression(plainSelect.getTop().getExpression());
    }

    // Priority 3: FETCH clause (DB2/Oracle 12c+)
    if (plainSelect.getFetch() != null) {
      long rowCount = plainSelect.getFetch().getRowCount();
      if (rowCount > 0) {
        return rowCount;
      }
    }

    return null;
  }

  /**
   * Extracts offset value from pagination clause.
   *
   * <p>Supports extraction from:</p>
   * <ul>
   *   <li>PlainSelect.getOffset() - Standard OFFSET keyword (SQL Server 2012+, PostgreSQL)</li>
   *   <li>Limit.getOffset() - MySQL comma syntax (LIMIT offset, count)</li>
   * </ul>
   *
   * @param plainSelect the PlainSelect statement (nullable)
   * @param limit the Limit clause (nullable, for MySQL comma syntax)
   * @return offset value, or null if not found or not a numeric literal
   */
  public static Long extractOffset(PlainSelect plainSelect, Limit limit) {
    if (plainSelect == null) {
      return null;
    }

    // Priority 1: Standard OFFSET keyword (SQL Server 2012+, PostgreSQL)
    if (plainSelect.getOffset() != null && plainSelect.getOffset().getOffset() != null) {
      return parseExpression(plainSelect.getOffset().getOffset());
    }

    // Priority 2: MySQL comma syntax (LIMIT offset, count)
    if (limit != null && limit.getOffset() != null) {
      return parseExpression(limit.getOffset());
    }

    return null;
  }

  // ==================== Private Helpers ====================

  /**
   * Parses an Expression to extract its Long value.
   *
   * <p>Handles LongValue directly and attempts to parse string representation
   * for other expression types.</p>
   *
   * @param expr the expression to parse
   * @return Long value, or null if parsing fails
   */
  private static Long parseExpression(Expression expr) {
    if (expr == null) {
      return null;
    }

    // Direct extraction for LongValue
    if (expr instanceof LongValue) {
      return ((LongValue) expr).getValue();
    }

    // Attempt string parsing for other types
    try {
      return Long.parseLong(expr.toString().trim());
    } catch (NumberFormatException e) {
      // Parameter placeholder (e.g., "?") or complex expression
      return null;
    }
  }
}
