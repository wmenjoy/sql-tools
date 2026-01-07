package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule checker that detects SQL injection via multi-statement execution.
 *
 * <p>MultiStatementChecker validates SQL strings to detect multi-statement attacks
 * where attackers append malicious statements after a semicolon, such as:</p>
 * <pre>{@code
 * SELECT * FROM users WHERE id = 1; DROP TABLE users--
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - This is the most severe validation check
 * as multi-statement injection can lead to complete database compromise.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses SQL string parsing for semicolon detection because JSqlParser Statement AST
 * doesn't preserve statement separators. The detection logic:</p>
 * <ol>
 *   <li>Find semicolons in SQL string</li>
 *   <li>Exclude trailing semicolons (end of SQL)</li>
 *   <li>Exclude semicolons within string literals (', ", ``)</li>
 *   <li>Report CRITICAL violation if multiple statements detected</li>
 * </ol>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Single statement: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>Trailing semicolon: {@code SELECT * FROM users WHERE id = 1;}</li>
 *   <li>Semicolon in string: {@code SELECT * FROM users WHERE name = 'John; test'}</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>Classic injection: {@code SELECT * FROM users; DROP TABLE users--}</li>
 *   <li>Stacked queries: {@code SELECT 1; SELECT 2; SELECT 3}</li>
 *   <li>Data exfiltration: {@code SELECT * FROM users; INSERT INTO hacker_table...}</li>
 * </ul>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to check raw SQL</li>
 *   <li>Uses raw SQL string from SqlContext, not parsed Statement</li>
 *   <li>Handles escaped quotes ('' in SQL) correctly</li>
 *   <li>Multi-dialect support: MySQL, Oracle, PostgreSQL</li>
 * </ul>
 *
 * @see RuleChecker
 * @see MultiStatementConfig
 * @since 1.0.0
 */
public class MultiStatementChecker implements RuleChecker {

  private static final Logger logger = LoggerFactory.getLogger(MultiStatementChecker.class);

  private final MultiStatementConfig config;

  /**
   * Creates a MultiStatementChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public MultiStatementChecker(MultiStatementConfig config) {
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
   * Checks SQL for multi-statement injection.
   *
   * <p>This method directly analyzes the raw SQL string for semicolons
   * that indicate multiple statements. It does NOT rely on the parsed
   * Statement AST because JSqlParser may fail to parse multi-statement SQL
   * or may only parse the first statement.</p>
   *
   * @param context the SQL execution context
   * @param result the validation result accumulator
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (!isEnabled()) {
      return;
    }

    String sql = context.getSql();
    if (sql == null || sql.trim().isEmpty()) {
      return;
    }

    if (hasMultipleStatements(sql)) {
      result.addViolation(
          RiskLevel.CRITICAL,
          "检测到多语句执行(SQL注入风险),请确保SQL不包含多个语句",
          "移除SQL中的分号或确保输入已正确转义"
      );
    }
  }

  // ==================== StatementVisitor Methods (no-op) ====================
  // These methods are required by the RuleChecker interface but are not used
  // because multi-statement detection works on raw SQL, not parsed AST.

  @Override
  public void visitSelect(Select select, SqlContext context) {
    // No-op: Detection is done in check() method on raw SQL
  }

  @Override
  public void visitUpdate(Update update, SqlContext context) {
    // No-op: Detection is done in check() method on raw SQL
  }

  @Override
  public void visitDelete(Delete delete, SqlContext context) {
    // No-op: Detection is done in check() method on raw SQL
  }

  @Override
  public void visitInsert(Insert insert, SqlContext context) {
    // No-op: Detection is done in check() method on raw SQL
  }

  /**
   * Detects if SQL contains multiple statements (semicolons outside string literals).
   *
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Track whether we're inside a string literal</li>
   *   <li>Handle different quote types: ', ", `</li>
   *   <li>Handle escaped quotes ('' in SQL)</li>
   *   <li>When semicolon found outside strings, check if it's trailing</li>
   *   <li>Non-trailing semicolon = multi-statement attack</li>
   * </ol>
   *
   * @param sql the SQL string to check
   * @return true if multiple statements detected, false otherwise
   */
  private boolean hasMultipleStatements(String sql) {
    if (sql == null || sql.isEmpty()) {
      return false;
    }

    char[] chars = sql.toCharArray();
    int len = chars.length;

    // Track string literal state
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inBacktick = false;

    for (int i = 0; i < len; i++) {
      char c = chars[i];

      // Handle string literal boundaries
      if (c == '\'' && !inDoubleQuote && !inBacktick) {
        // Check for escaped quote ('')
        if (inSingleQuote && i + 1 < len && chars[i + 1] == '\'') {
          i++; // Skip escaped quote
          continue;
        }
        inSingleQuote = !inSingleQuote;
        continue;
      }

      if (c == '"' && !inSingleQuote && !inBacktick) {
        // Check for escaped quote ("")
        if (inDoubleQuote && i + 1 < len && chars[i + 1] == '"') {
          i++; // Skip escaped quote
          continue;
        }
        inDoubleQuote = !inDoubleQuote;
        continue;
      }

      if (c == '`' && !inSingleQuote && !inDoubleQuote) {
        // Check for escaped backtick (``)
        if (inBacktick && i + 1 < len && chars[i + 1] == '`') {
          i++; // Skip escaped backtick
          continue;
        }
        inBacktick = !inBacktick;
        continue;
      }

      // Check for semicolon outside string literals
      if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
        // Check if this is a trailing semicolon
        if (!isTrailingSemicolon(chars, i)) {
          return true; // Non-trailing semicolon = multi-statement
        }
      }
    }

    return false;
  }

  /**
   * Checks if the semicolon at the given position is a trailing semicolon.
   *
   * <p>A trailing semicolon is one that is followed only by:</p>
   * <ul>
   *   <li>Whitespace</li>
   *   <li>SQL comments (-- or block comments)</li>
   *   <li>More semicolons (which are also trailing)</li>
   *   <li>End of string</li>
   * </ul>
   *
   * @param chars the SQL character array
   * @param semicolonIndex the index of the semicolon
   * @return true if this is a trailing semicolon, false otherwise
   */
  private boolean isTrailingSemicolon(char[] chars, int semicolonIndex) {
    int len = chars.length;

    for (int i = semicolonIndex + 1; i < len; i++) {
      char c = chars[i];

      // Skip whitespace
      if (Character.isWhitespace(c)) {
        continue;
      }

      // Skip additional semicolons (they're also trailing)
      if (c == ';') {
        continue;
      }

      // Check for line comment (--)
      if (c == '-' && i + 1 < len && chars[i + 1] == '-') {
        // Skip to end of line or string
        while (i < len && chars[i] != '\n') {
          i++;
        }
        continue;
      }

      // Check for block comment (/* */)
      if (c == '/' && i + 1 < len && chars[i + 1] == '*') {
        // Skip to end of block comment
        i += 2;
        while (i + 1 < len && !(chars[i] == '*' && chars[i + 1] == '/')) {
          i++;
        }
        i++; // Skip the closing */
        continue;
      }

      // Found non-whitespace, non-comment content after semicolon
      // This means there's another statement
      return false;
    }

    // Only whitespace/comments/semicolons after this semicolon
    return true;
  }
}
