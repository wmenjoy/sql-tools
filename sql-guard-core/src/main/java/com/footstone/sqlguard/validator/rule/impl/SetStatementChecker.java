package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule checker that detects session variable modification statements (SET).
 *
 * <p>SetStatementChecker validates SQL strings to detect SET statements that can be used
 * for transaction isolation bypass and SQL mode manipulation attacks, such as:</p>
 * <pre>{@code
 * SET autocommit = 0
 * SET sql_mode = ''
 * SET @user_id = 123
 * SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> MEDIUM - SET statements can cause unexpected behavior
 * but are less severe than structural changes like DROP or TRUNCATE.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses pattern matching on the SQL string to detect SET keyword at statement start.
 * This approach is necessary because JSqlParser may not fully parse all SET statement
 * variants across different database dialects.</p>
 *
 * <p><strong>CRITICAL: UPDATE vs SET Differentiation:</strong></p>
 * <p>This checker correctly differentiates between SET statement and UPDATE...SET column
 * assignment:</p>
 * <ul>
 *   <li>SET statement (DETECT): {@code SET autocommit = 0}</li>
 *   <li>UPDATE statement (ALLOW): {@code UPDATE user SET name = 'test'} (column assignment)</li>
 * </ul>
 * <p>The differentiation is done by checking if UPDATE keyword precedes SET keyword.
 * If UPDATE is found before SET, it's an UPDATE statement and should be allowed.</p>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>SELECT: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>INSERT: {@code INSERT INTO users (name) VALUES ('test')}</li>
 *   <li>DELETE: {@code DELETE FROM users WHERE id = 1}</li>
 *   <li>UPDATE (column assignment): {@code UPDATE user SET name = 'test'}</li>
 *   <li>OFFSET keyword: {@code SELECT * FROM users LIMIT 10 OFFSET 20}</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>Disable auto-commit: {@code SET autocommit = 0}</li>
 *   <li>Remove SQL safety: {@code SET sql_mode = ''}</li>
 *   <li>User variable: {@code SET @admin_id = 1}</li>
 *   <li>Character set: {@code SET NAMES utf8mb4}, {@code SET CHARSET utf8}</li>
 *   <li>Session variable: {@code SET SESSION wait_timeout = 28800}</li>
 *   <li>Global variable: {@code SET GLOBAL max_connections = 1000}</li>
 *   <li>Transaction isolation: {@code SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED}</li>
 *   <li>PostgreSQL: {@code SET search_path TO myschema}</li>
 * </ul>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to check raw SQL</li>
 *   <li>Uses raw SQL string from SqlContext, not parsed Statement</li>
 *   <li>Case-insensitive matching for SET keyword</li>
 *   <li>Multi-dialect support: MySQL, PostgreSQL</li>
 * </ul>
 *
 * @see RuleChecker
 * @see SetStatementConfig
 * @since 1.0.0
 */
public class SetStatementChecker implements RuleChecker {

  private final SetStatementConfig config;

  /**
   * Pattern to detect SET statement at the beginning of SQL.
   * Matches: SET followed by whitespace and variable name
   * Case-insensitive matching.
   * Uses word boundary (\b) to avoid matching 'OFFSET', 'RESET', etc.
   *
   * Pattern explanation:
   * - ^\s* : Start of string, optional whitespace
   * - SET : The SET keyword (case-insensitive via Pattern.CASE_INSENSITIVE)
   * - \s+ : Required whitespace after SET
   * - (\S+) : Capture group for variable name (non-whitespace characters)
   */
  private static final Pattern SET_STATEMENT_PATTERN = Pattern.compile(
      "^\\s*SET\\s+(\\S+)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect UPDATE keyword at the beginning of SQL.
   * Used to differentiate UPDATE...SET from SET statement.
   *
   * Pattern explanation:
   * - ^\s* : Start of string, optional whitespace
   * - UPDATE : The UPDATE keyword (case-insensitive via Pattern.CASE_INSENSITIVE)
   * - \s+ : Required whitespace after UPDATE
   */
  private static final Pattern UPDATE_PATTERN = Pattern.compile(
      "^\\s*UPDATE\\s+",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Creates a SetStatementChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public SetStatementChecker(SetStatementConfig config) {
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
   * Checks SQL for SET statement.
   *
   * <p>This method directly analyzes the raw SQL string for SET keyword
   * at statement start. It does NOT rely on the parsed Statement AST
   * because JSqlParser may not fully parse all SET statement variants.</p>
   *
   * <p>Detection algorithm:</p>
   * <ol>
   *   <li>Skip if checker is disabled</li>
   *   <li>Skip if SQL is null or empty</li>
   *   <li>Check if SQL starts with UPDATE (if so, it's UPDATE...SET, allow)</li>
   *   <li>Check if SQL starts with SET (if so, detect as SET statement)</li>
   *   <li>Extract variable name if parseable for violation message</li>
   * </ol>
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

    // CRITICAL: Check if this is an UPDATE statement first
    // UPDATE...SET is column assignment, NOT a SET statement
    if (isUpdateStatement(sql)) {
      return; // Allow UPDATE...SET
    }

    // Check for SET statement
    Matcher setMatcher = SET_STATEMENT_PATTERN.matcher(sql);
    if (setMatcher.find()) {
      String variableName = setMatcher.group(1);
      String message = buildViolationMessage(variableName);
      result.addViolation(
          RiskLevel.MEDIUM,
          message,
          "移除SET语句或将其配置在应用层/连接池初始化阶段，而非运行时SQL中"
      );
    }
  }

  /**
   * Checks if the SQL is an UPDATE statement.
   *
   * <p>This method is used to differentiate UPDATE...SET (column assignment)
   * from SET statement. If UPDATE keyword is found at the beginning,
   * the SQL is an UPDATE statement and should be allowed.</p>
   *
   * @param sql the SQL string to check
   * @return true if this is an UPDATE statement, false otherwise
   */
  private boolean isUpdateStatement(String sql) {
    return UPDATE_PATTERN.matcher(sql).find();
  }

  /**
   * Builds a violation message including the variable name if parseable.
   *
   * @param variableName the variable name from SET statement
   * @return the violation message
   */
  private String buildViolationMessage(String variableName) {
    StringBuilder message = new StringBuilder();
    message.append("检测到SET语句: ");

    if (variableName != null && !variableName.isEmpty()) {
      message.append(variableName);
      message.append(" (可能存在会话变量修改风险)");
    } else {
      message.append("(可能存在会话变量修改风险)");
    }

    return message.toString();
  }

  // ==================== StatementVisitor Methods (no-op) ====================
  // These methods are required by the RuleChecker interface but are not used
  // because SET statement detection works on raw SQL, not parsed AST.

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
}
