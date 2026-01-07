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
 * Rule checker that detects metadata disclosure statements (SHOW/DESCRIBE/USE).
 *
 * <p>MetadataStatementChecker validates SQL statements to detect metadata commands that
 * can leak schema information to attackers. For example:</p>
 * <pre>{@code
 * SHOW TABLES
 * SHOW DATABASES
 * DESCRIBE users
 * DESC orders
 * USE production_db
 * }</pre>
 *
 * <p><strong>Risk Level:</strong> HIGH - Metadata queries can enable attackers to:
 * <ul>
 *   <li>Discover table structures for targeted SQL injection attacks</li>
 *   <li>Enumerate databases for reconnaissance</li>
 *   <li>Learn schema information to plan exploitation</li>
 *   <li>Identify sensitive tables and columns</li>
 * </ul>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses pattern matching on the SQL string to detect metadata keywords at statement start.
 * This approach is necessary because metadata commands are not standard DML and may not be
 * parsed as typical SELECT/UPDATE/DELETE/INSERT statements.</p>
 *
 * <p><strong>INFORMATION_SCHEMA Differentiation:</strong></p>
 * <p>This checker correctly differentiates between metadata commands and proper
 * INFORMATION_SCHEMA queries:</p>
 * <ul>
 *   <li>Metadata Command (BLOCK by default): {@code SHOW TABLES}</li>
 *   <li>Metadata Command (BLOCK by default): {@code DESCRIBE users}</li>
 *   <li>INFORMATION_SCHEMA Query (ALLOW): {@code SELECT * FROM INFORMATION_SCHEMA.TABLES}</li>
 * </ul>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Normal DML: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>INSERT: {@code INSERT INTO users (name) VALUES ('test')}</li>
 *   <li>UPDATE: {@code UPDATE users SET name = 'new' WHERE id = 1}</li>
 *   <li>DELETE: {@code DELETE FROM users WHERE id = 1}</li>
 *   <li>INFORMATION_SCHEMA: {@code SELECT * FROM INFORMATION_SCHEMA.TABLES}</li>
 *   <li>Allowed statements: When configured in allowedStatements list</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>Table enumeration: {@code SHOW TABLES}</li>
 *   <li>Database enumeration: {@code SHOW DATABASES}</li>
 *   <li>Table structure: {@code DESCRIBE users}</li>
 *   <li>Table structure: {@code DESC users}</li>
 *   <li>Database switching: {@code USE production_db}</li>
 *   <li>Filtered SHOW: {@code SHOW TABLES LIKE 'user%'}</li>
 *   <li>SHOW with options: {@code SHOW COLUMNS FROM users}</li>
 * </ul>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to check raw SQL</li>
 *   <li>Uses raw SQL string from SqlContext, not parsed Statement</li>
 *   <li>Multi-dialect support: MySQL SHOW/DESCRIBE/DESC/USE</li>
 * </ul>
 *
 * @see RuleChecker
 * @see MetadataStatementConfig
 * @since 1.0.0
 */
public class MetadataStatementChecker implements RuleChecker {

  private static final Logger logger = LoggerFactory.getLogger(MetadataStatementChecker.class);

  private final MetadataStatementConfig config;

  /**
   * Pattern to detect SHOW statements at the beginning.
   * Matches: SHOW TABLES, SHOW DATABASES, SHOW COLUMNS, etc.
   * Case-insensitive matching with word boundary.
   */
  private static final Pattern SHOW_PATTERN = Pattern.compile(
      "^\\s*SHOW\\s+",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect DESCRIBE statements at the beginning.
   * Matches: DESCRIBE table, DESC table
   * Case-insensitive matching with word boundary.
   */
  private static final Pattern DESCRIBE_PATTERN = Pattern.compile(
      "^\\s*(DESCRIBE|DESC)\\s+",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to detect USE statements at the beginning.
   * Matches: USE database_name
   * Case-insensitive matching with word boundary.
   */
  private static final Pattern USE_PATTERN = Pattern.compile(
      "^\\s*USE\\s+",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to extract the specific SHOW command type.
   * Captures: SHOW TABLES, SHOW DATABASES, SHOW COLUMNS, etc.
   */
  private static final Pattern SHOW_TYPE_PATTERN = Pattern.compile(
      "^\\s*SHOW\\s+(\\w+)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to extract the table name from DESCRIBE/DESC.
   * Captures the table name after DESCRIBE or DESC.
   */
  private static final Pattern DESCRIBE_TABLE_PATTERN = Pattern.compile(
      "^\\s*(DESCRIBE|DESC)\\s+(\\S+)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Pattern to extract the database name from USE.
   * Captures the database name after USE.
   */
  private static final Pattern USE_DB_PATTERN = Pattern.compile(
      "^\\s*USE\\s+(\\S+)",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Creates a MetadataStatementChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public MetadataStatementChecker(MetadataStatementConfig config) {
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
   * Validates SQL statements for metadata disclosure commands.
   *
   * <p>This method directly analyzes the raw SQL string for metadata keywords
   * at statement start. It does NOT rely on the parsed Statement AST because
   * metadata commands (SHOW/DESCRIBE/USE) are not standard DML statements.</p>
   *
   * <p>Detection algorithm:</p>
   * <ol>
   *   <li>Skip if checker is disabled</li>
   *   <li>Get raw SQL string from context</li>
   *   <li>Check for SHOW pattern at statement start</li>
   *   <li>Check for DESCRIBE/DESC pattern at statement start</li>
   *   <li>Check for USE pattern at statement start</li>
   *   <li>Validate detected statement type against allowedStatements config</li>
   *   <li>Add HIGH violation if metadata command detected and not allowed</li>
   * </ol>
   *
   * @param context the SQL execution context
   * @param result  the validation result accumulator
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
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

    // Trim the SQL for pattern matching
    String trimmedSql = sql.trim();

    // Check for SHOW statements
    if (SHOW_PATTERN.matcher(trimmedSql).find()) {
      if (!config.isStatementAllowed("SHOW")) {
        String showType = extractShowType(trimmedSql);
        result.addViolation(
            RiskLevel.HIGH,
            "检测到元数据语句: SHOW " + showType + " (可能泄露数据库架构信息)",
            "移除SHOW命令，使用SELECT查询INFORMATION_SCHEMA获取元数据信息"
        );
      }
      return;
    }

    // Check for DESCRIBE/DESC statements
    if (DESCRIBE_PATTERN.matcher(trimmedSql).find()) {
      if (!config.isStatementAllowed("DESCRIBE")) {
        String tableName = extractDescribeTable(trimmedSql);
        result.addViolation(
            RiskLevel.HIGH,
            "检测到元数据语句: DESCRIBE " + tableName + " (可能泄露表结构信息)",
            "移除DESCRIBE命令，使用SELECT查询INFORMATION_SCHEMA.COLUMNS获取列信息"
        );
      }
      return;
    }

    // Check for USE statements
    if (USE_PATTERN.matcher(trimmedSql).find()) {
      if (!config.isStatementAllowed("USE")) {
        String dbName = extractUseDatabase(trimmedSql);
        result.addViolation(
            RiskLevel.HIGH,
            "检测到元数据语句: USE " + dbName + " (可能用于数据库枚举攻击)",
            "移除USE命令，在连接字符串中指定数据库或使用完全限定表名"
        );
      }
    }

    // Normal DML statements (SELECT/INSERT/UPDATE/DELETE) and INFORMATION_SCHEMA queries pass through
  }

  // ==================== StatementVisitor Methods (no-op) ====================
  // These methods are required by the RuleChecker interface but are not used
  // because metadata statement detection works on raw SQL, not parsed AST.

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
   * Extracts the SHOW command type (e.g., TABLES, DATABASES, COLUMNS).
   *
   * @param sql the SQL statement
   * @return the SHOW command type or "UNKNOWN" if not found
   */
  private String extractShowType(String sql) {
    Matcher matcher = SHOW_TYPE_PATTERN.matcher(sql);
    if (matcher.find()) {
      return matcher.group(1).toUpperCase();
    }
    return "UNKNOWN";
  }

  /**
   * Extracts the table name from DESCRIBE/DESC statement.
   *
   * @param sql the SQL statement
   * @return the table name or "UNKNOWN" if not found
   */
  private String extractDescribeTable(String sql) {
    Matcher matcher = DESCRIBE_TABLE_PATTERN.matcher(sql);
    if (matcher.find()) {
      return matcher.group(2);
    }
    return "UNKNOWN";
  }

  /**
   * Extracts the database name from USE statement.
   *
   * @param sql the SQL statement
   * @return the database name or "UNKNOWN" if not found
   */
  private String extractUseDatabase(String sql) {
    Matcher matcher = USE_DB_PATTERN.matcher(sql);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "UNKNOWN";
  }
}
