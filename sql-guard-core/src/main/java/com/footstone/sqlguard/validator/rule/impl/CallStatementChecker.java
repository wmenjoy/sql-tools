package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule checker that detects stored procedure calls (CALL/EXECUTE/EXEC).
 *
 * <p>CallStatementChecker validates SQL statements to detect stored procedure calls that
 * introduce opaque logic and potential permission escalation risks. For example:</p>
 * <pre>{@code
 * CALL sp_user_create(1, 'test')
 * EXECUTE sp_delete_user @user_id = 1
 * EXEC sp_update_permissions
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> HIGH - Stored procedure calls can:
 * <ul>
 *   <li>Introduce opaque logic not visible in application code</li>
 *   <li>Execute with elevated (definer) privileges</li>
 *   <li>Hide business logic complexity in database layer</li>
 *   <li>Make security auditing more difficult</li>
 * </ul>
 *
 * <p><strong>Default Strategy:</strong> WARN (not BLOCK)</p>
 * <p>Stored procedures may be legitimate architecture in some systems. The default
 * WARN strategy allows monitoring without breaking existing functionality.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses pattern matching on the SQL string to detect CALL, EXECUTE, and EXEC
 * keywords at the start of statements (case-insensitive). This approach covers:</p>
 * <ul>
 *   <li>MySQL: CALL procedure_name(params)</li>
 *   <li>Oracle: EXECUTE procedure_name(params)</li>
 *   <li>SQL Server: EXEC procedure_name params</li>
 * </ul>
 *
 * <p><strong>Differentiating Procedures from Functions:</strong></p>
 * <p>This checker correctly differentiates between stored procedure calls and
 * function calls in expressions:</p>
 * <ul>
 *   <li>Procedure (DETECT): {@code CALL sp_user_create(1)} → Standalone call</li>
 *   <li>Function (ALLOW): {@code SELECT MAX(id) FROM users} → Function in expression</li>
 *   <li>Function (ALLOW): {@code SELECT UPPER(name) FROM users} → Function in expression</li>
 * </ul>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>SELECT with functions: {@code SELECT MAX(id), COUNT(*) FROM users}</li>
 *   <li>UPDATE with functions: {@code UPDATE users SET name = UPPER(name)}</li>
 *   <li>INSERT statements: {@code INSERT INTO users (name) VALUES ('test')}</li>
 *   <li>DELETE statements: {@code DELETE FROM users WHERE id = 1}</li>
 * </ul>
 *
 * <p><strong>Procedure Patterns (Fail):</strong></p>
 * <ul>
 *   <li>MySQL CALL: {@code CALL sp_user_create(1, 'test')}</li>
 *   <li>Oracle EXECUTE: {@code EXECUTE sp_process_order(100)}</li>
 *   <li>SQL Server EXEC: {@code EXEC sp_delete_user @id = 1}</li>
 *   <li>Case variations: {@code call sp_test()}, {@code Call sp_test()}</li>
 * </ul>
 *
 * @see AbstractRuleChecker
 * @see CallStatementConfig
 * @since 1.0.0
 */
public class CallStatementChecker extends AbstractRuleChecker {

  private final CallStatementConfig config;

  /**
   * Pattern to detect MySQL CALL statement.
   * Matches: CALL procedure_name or CALL procedure_name(params)
   * Case-insensitive, captures procedure name.
   */
  private static final Pattern CALL_PATTERN = Pattern.compile(
      "^\\s*CALL\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*(?:\\(|$|;)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect Oracle/SQL Server EXECUTE statement.
   * Matches: EXECUTE procedure_name or EXECUTE procedure_name(params)
   * Case-insensitive, captures procedure name.
   */
  private static final Pattern EXECUTE_PATTERN = Pattern.compile(
      "^\\s*EXECUTE\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*(?:\\(|@|$|;)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect SQL Server EXEC statement.
   * Matches: EXEC procedure_name or EXEC procedure_name params
   * Case-insensitive, captures procedure name.
   * Note: Must not be followed by 'UTE' to avoid matching EXECUTE.
   */
  private static final Pattern EXEC_PATTERN = Pattern.compile(
      "^\\s*EXEC\\s+(?!UTE)([a-zA-Z_][a-zA-Z0-9_.]*)\\s*(?:\\(|@|$|;|\\s)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Creates a CallStatementChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public CallStatementChecker(CallStatementConfig config) {
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
   * Validates the raw SQL string for stored procedure calls.
   *
   * <p>This method is called for all statement types to check the raw SQL
   * for procedure call patterns (CALL/EXECUTE/EXEC). The detection is done
   * on the raw SQL string because JSqlParser may not parse procedure calls
   * as standard DML statements.</p>
   *
   * @param context the SQL execution context
   */
  private void checkForProcedureCall(SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    if (context == null) {
      return;
    }

    String sql = context.getSql();
    if (sql == null || sql.trim().isEmpty()) {
      return;
    }

    // Check for CALL statement (MySQL)
    Matcher callMatcher = CALL_PATTERN.matcher(sql);
    if (callMatcher.find()) {
      String procedureName = callMatcher.group(1);
      addViolation(
          RiskLevel.HIGH,
          "检测到存储过程调用: CALL " + procedureName + " (存储过程引入不透明逻辑，可能存在权限提升风险)",
          "将存储过程逻辑迁移到应用层，使用直接SQL语句代替存储过程调用"
      );
      return;
    }

    // Check for EXECUTE statement (Oracle)
    Matcher executeMatcher = EXECUTE_PATTERN.matcher(sql);
    if (executeMatcher.find()) {
      String procedureName = executeMatcher.group(1);
      addViolation(
          RiskLevel.HIGH,
          "检测到存储过程调用: EXECUTE " + procedureName + " (存储过程引入不透明逻辑，可能存在权限提升风险)",
          "将存储过程逻辑迁移到应用层，使用直接SQL语句代替存储过程调用"
      );
      return;
    }

    // Check for EXEC statement (SQL Server)
    Matcher execMatcher = EXEC_PATTERN.matcher(sql);
    if (execMatcher.find()) {
      String procedureName = execMatcher.group(1);
      addViolation(
          RiskLevel.HIGH,
          "检测到存储过程调用: EXEC " + procedureName + " (存储过程引入不透明逻辑，可能存在权限提升风险)",
          "将存储过程逻辑迁移到应用层，使用直接SQL语句代替存储过程调用"
      );
    }
  }

  /**
   * Validates SELECT statements - checks raw SQL for procedure calls.
   *
   * <p>Note: SELECT statements with function calls like MAX(id) should pass.
   * Only standalone CALL/EXECUTE/EXEC statements are flagged.</p>
   *
   * @param select  the SELECT statement
   * @param context the SQL execution context
   */
  @Override
  public void visitSelect(Select select, SqlContext context) {
    checkForProcedureCall(context);
  }

  /**
   * Validates UPDATE statements - checks raw SQL for procedure calls.
   *
   * @param update  the UPDATE statement
   * @param context the SQL execution context
   */
  @Override
  public void visitUpdate(Update update, SqlContext context) {
    checkForProcedureCall(context);
  }

  /**
   * Validates DELETE statements - checks raw SQL for procedure calls.
   *
   * @param delete  the DELETE statement
   * @param context the SQL execution context
   */
  @Override
  public void visitDelete(Delete delete, SqlContext context) {
    checkForProcedureCall(context);
  }

  /**
   * Validates INSERT statements - checks raw SQL for procedure calls.
   *
   * @param insert  the INSERT statement
   * @param context the SQL execution context
   */
  @Override
  public void visitInsert(Insert insert, SqlContext context) {
    checkForProcedureCall(context);
  }

  /**
   * Validates raw SQL when parsing fails - checks for procedure calls.
   *
   * <p>This method is called when JSqlParser cannot parse the SQL statement,
   * which is common for CALL/EXECUTE/EXEC statements that are not standard
   * DML statements. The raw SQL pattern matching still works in this case.</p>
   *
   * @param context the SQL execution context (with null Statement)
   */
  @Override
  protected void visitRawSql(SqlContext context) {
    checkForProcedureCall(context);
  }
}
