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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule checker that detects MySQL file write operations (SELECT INTO OUTFILE/DUMPFILE).
 *
 * <p>IntoOutfileChecker validates SELECT statements to detect file write operations that can be
 * used for arbitrary file writes and data exfiltration attacks. For example:</p>
 * <pre>{@code
 * SELECT * INTO OUTFILE '/tmp/data.txt' FROM users
 * SELECT '<?php system($_GET["cmd"]); ?>' INTO OUTFILE '/var/www/html/shell.php'
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - File write operations enable attackers to:
 * <ul>
 *   <li>Write arbitrary files to the file system</li>
 *   <li>Exfiltrate sensitive data to attacker-controlled paths</li>
 *   <li>Create web shells for remote code execution</li>
 *   <li>Modify system configuration files</li>
 * </ul>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses pattern matching on the raw SQL string to detect INTO OUTFILE and INTO DUMPFILE
 * keywords. This approach is necessary because JSqlParser cannot parse MySQL-specific
 * INTO OUTFILE/DUMPFILE syntax. By implementing RuleChecker directly (not extending
 * AbstractRuleChecker), this checker can analyze raw SQL even when parsing fails.</p>
 *
 * <p><strong>Oracle Syntax Differentiation:</strong></p>
 * <p>This checker correctly differentiates between MySQL file operations and Oracle
 * variable assignment syntax:</p>
 * <ul>
 *   <li>MySQL (BLOCK): {@code SELECT * INTO OUTFILE '/path' FROM users}</li>
 *   <li>MySQL (BLOCK): {@code SELECT * INTO DUMPFILE '/path' FROM users}</li>
 *   <li>Oracle (ALLOW): {@code SELECT id INTO v_id FROM users} (variable assignment)</li>
 * </ul>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Simple SELECT: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>SELECT with JOIN: {@code SELECT u.* FROM users u JOIN orders o ON u.id = o.user_id}</li>
 *   <li>Oracle INTO variable: {@code SELECT id INTO v_id FROM users WHERE id = 1}</li>
 *   <li>Oracle BULK COLLECT: {@code SELECT id, name INTO v_ids, v_names FROM users}</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>File write: {@code SELECT * INTO OUTFILE '/tmp/data.txt' FROM users}</li>
 *   <li>Binary dump: {@code SELECT * INTO DUMPFILE '/tmp/dump.bin' FROM users}</li>
 *   <li>Web shell: {@code SELECT '<?php...' INTO OUTFILE '/var/www/html/shell.php'}</li>
 *   <li>Data exfiltration: {@code SELECT password INTO OUTFILE '/tmp/creds.txt' FROM users}</li>
 *   <li>Path traversal: {@code SELECT * INTO OUTFILE '/tmp/../../../etc/cron.d/job'}</li>
 * </ul>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to check raw SQL</li>
 *   <li>Uses raw SQL string from SqlContext, not parsed Statement</li>
 *   <li>Pattern matching ensures detection even when JSqlParser fails to parse</li>
 * </ul>
 *
 * @see RuleChecker
 * @see IntoOutfileConfig
 * @since 1.0.0
 */
public class IntoOutfileChecker implements RuleChecker {

  private static final Logger logger = LoggerFactory.getLogger(IntoOutfileChecker.class);

  private final IntoOutfileConfig config;

  /**
   * Pattern to detect INTO OUTFILE with file path.
   * Matches: INTO OUTFILE 'path' or INTO OUTFILE "path"
   * Case-insensitive matching.
   */
  private static final Pattern INTO_OUTFILE_PATTERN = Pattern.compile(
      "\\bINTO\\s+OUTFILE\\s+['\"]([^'\"]*)['\"]",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect INTO DUMPFILE with file path.
   * Matches: INTO DUMPFILE 'path' or INTO DUMPFILE "path"
   * Case-insensitive matching.
   */
  private static final Pattern INTO_DUMPFILE_PATTERN = Pattern.compile(
      "\\bINTO\\s+DUMPFILE\\s+['\"]([^'\"]*)['\"]",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Creates an IntoOutfileChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public IntoOutfileChecker(IntoOutfileConfig config) {
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
   * Checks SQL for MySQL file write operations (INTO OUTFILE/DUMPFILE).
   *
   * <p>This method directly analyzes the raw SQL string for file write patterns.
   * It does NOT rely on the parsed Statement AST because JSqlParser cannot parse
   * MySQL's INTO OUTFILE/DUMPFILE syntax.</p>
   *
   * <p>Detection algorithm:</p>
   * <ol>
   *   <li>Skip if checker is disabled</li>
   *   <li>Get raw SQL string from context</li>
   *   <li>Check for INTO OUTFILE pattern using regex</li>
   *   <li>Check for INTO DUMPFILE pattern using regex</li>
   *   <li>Add CRITICAL violation if file operation detected</li>
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

    // Check for INTO OUTFILE
    Matcher outfileMatcher = INTO_OUTFILE_PATTERN.matcher(sql);
    if (outfileMatcher.find()) {
      String filePath = outfileMatcher.group(1);
      result.addViolation(
          RiskLevel.CRITICAL,
          "检测到MySQL文件写入操作: INTO OUTFILE '" + filePath + "' (可能存在任意文件写入攻击风险)",
          "移除INTO OUTFILE子句，使用应用层导出功能代替数据库文件写入"
      );
      return;
    }

    // Check for INTO DUMPFILE
    Matcher dumpfileMatcher = INTO_DUMPFILE_PATTERN.matcher(sql);
    if (dumpfileMatcher.find()) {
      String filePath = dumpfileMatcher.group(1);
      result.addViolation(
          RiskLevel.CRITICAL,
          "检测到MySQL文件写入操作: INTO DUMPFILE '" + filePath + "' (可能存在二进制文件写入攻击风险)",
          "移除INTO DUMPFILE子句，使用应用层导出功能代替数据库文件写入"
      );
    }
  }

  // ==================== StatementVisitor Methods (no-op) ====================
  // These methods are required by the RuleChecker interface but are not used
  // because file operation detection works on raw SQL, not parsed AST.

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
