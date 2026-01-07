package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule checker that detects SQL comments used for injection attacks.
 *
 * <p>SqlCommentChecker validates SQL strings to detect comments that can be used by attackers
 * to bypass security detection, hide malicious code, or comment out WHERE clauses.</p>
 *
 * <p><strong>Detected Comment Types:</strong></p>
 * <ul>
 *   <li><b>Single-line comments (--)</b>: SQL standard, used to comment out rest of line</li>
 *   <li><b>Multi-line comments (/* ... * /)</b>: SQL standard, can hide code anywhere</li>
 *   <li><b>MySQL comments (#)</b>: MySQL-specific, used to comment out rest of line</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - Comments enable attackers to:</p>
 * <ul>
 *   <li>Bypass security detection mechanisms</li>
 *   <li>Hide malicious code within seemingly harmless queries</li>
 *   <li>Comment out WHERE clauses to affect all records</li>
 *   <li>Inject payloads that evade input validation</li>
 * </ul>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <p>Uses SQL string parsing because JSqlParser does not preserve comments in the AST.
 * The detection logic:</p>
 * <ol>
 *   <li>Parse SQL string character by character</li>
 *   <li>Track string literal boundaries (', ", `) to avoid false positives</li>
 *   <li>Detect comment markers outside string literals</li>
 *   <li>Optionally allow Oracle optimizer hints (/*+ ... * /) when configured</li>
 * </ol>
 *
 * <p><strong>Safe Patterns (Pass):</strong></p>
 * <ul>
 *   <li>Normal SQL: {@code SELECT * FROM users WHERE id = 1}</li>
 *   <li>String containing comment marker: {@code SELECT * FROM users WHERE name = '--test'}</li>
 *   <li>Oracle hints when allowHintComments=true: {@code SELECT /*+ INDEX(users idx_email) * / * FROM users}</li>
 * </ul>
 *
 * <p><strong>Attack Patterns (Fail):</strong></p>
 * <ul>
 *   <li>Single-line comment: {@code SELECT * FROM users WHERE id = 1 -- AND password = 'secret'}</li>
 *   <li>Multi-line comment: {@code SELECT * FROM users /* WHERE id = 1 * /}</li>
 *   <li>MySQL comment: {@code SELECT * FROM users # WHERE id = 1}</li>
 *   <li>Comment to bypass auth: {@code SELECT * FROM users WHERE id = '1'-- AND password = 'x'}</li>
 * </ul>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to check raw SQL</li>
 *   <li>Uses raw SQL string from SqlContext, not parsed Statement</li>
 *   <li>Handles escaped quotes correctly ('' in SQL)</li>
 *   <li>Multi-dialect support: MySQL (#), Oracle (hints), PostgreSQL, SQL Server</li>
 *   <li>Performance: ~1-2ms overhead for string parsing</li>
 * </ul>
 *
 * @see RuleChecker
 * @see SqlCommentConfig
 * @since 1.0.0
 */
public class SqlCommentChecker implements RuleChecker {

  private static final Logger logger = LoggerFactory.getLogger(SqlCommentChecker.class);

  private final SqlCommentConfig config;

  /**
   * Creates a SqlCommentChecker with the specified configuration.
   *
   * @param config the configuration for this checker
   */
  public SqlCommentChecker(SqlCommentConfig config) {
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
   * Checks SQL for comment injection.
   *
   * <p>This method directly analyzes the raw SQL string for comment markers.
   * It does NOT rely on the parsed Statement AST because JSqlParser strips
   * comments during parsing.</p>
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

    // Detect comments in raw SQL
    List<CommentInfo> comments = detectComments(sql);

    // Filter out allowed hint comments if configured
    if (config.isAllowHintComments()) {
      comments = filterHintComments(comments, context);
    }

    // Report violations for remaining comments
    for (CommentInfo comment : comments) {
      result.addViolation(
          RiskLevel.CRITICAL,
          buildViolationMessage(comment),
          buildSuggestion(comment)
      );
    }
  }

  // ==================== StatementVisitor Methods (no-op) ====================
  // These methods are required by the RuleChecker interface but are not used
  // because comment detection works on raw SQL, not parsed AST.

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
   * Detects all comments in the SQL string.
   *
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Track whether we're inside a string literal</li>
   *   <li>Handle different quote types: ', ", `</li>
   *   <li>Handle escaped quotes ('' in SQL)</li>
   *   <li>When comment marker found outside strings, record it</li>
   * </ol>
   *
   * @param sql the SQL string to check
   * @return list of detected comments
   */
  private List<CommentInfo> detectComments(String sql) {
    List<CommentInfo> comments = new ArrayList<>();

    if (sql == null || sql.isEmpty()) {
      return comments;
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

      // Skip if inside string literal
      if (inSingleQuote || inDoubleQuote || inBacktick) {
        continue;
      }

      // Check for single-line comment (--)
      if (c == '-' && i + 1 < len && chars[i + 1] == '-') {
        int endPos = findLineEnd(chars, i + 2);
        String content = sql.substring(i, endPos);
        comments.add(new CommentInfo(CommentType.SINGLE_LINE, i, endPos, content));
        i = endPos - 1; // Skip to end of comment
        continue;
      }

      // Check for multi-line comment (/* */)
      if (c == '/' && i + 1 < len && chars[i + 1] == '*') {
        int endPos = findBlockCommentEnd(chars, i + 2);
        String content = sql.substring(i, Math.min(endPos, len));
        CommentType type = isHintComment(chars, i) ? CommentType.HINT : CommentType.MULTI_LINE;
        comments.add(new CommentInfo(type, i, endPos, content));
        i = endPos - 1; // Skip to end of comment
        continue;
      }

      // Check for MySQL comment (#)
      // Skip MyBatis parameter syntax: #{param} or ${param}
      if (c == '#') {
        // Check if this is MyBatis parameter syntax #{...}
        if (i + 1 < len && chars[i + 1] == '{') {
          // Skip MyBatis parameter - find closing }
          int closeBrace = findClosingBrace(chars, i + 2);
          if (closeBrace > i + 2) {
            i = closeBrace; // Skip to closing brace
            continue;
          }
        }
        // This is a real MySQL # comment
        int endPos = findLineEnd(chars, i + 1);
        String content = sql.substring(i, endPos);
        comments.add(new CommentInfo(CommentType.MYSQL_HASH, i, endPos, content));
        i = endPos - 1; // Skip to end of comment
        continue;
      }
    }

    return comments;
  }

  /**
   * Checks if a multi-line comment starting at position is an Oracle hint.
   *
   * <p>Oracle hints have the format: /*+ HINT_NAME ... * /</p>
   *
   * @param chars the SQL character array
   * @param startPos the position of '/' in '/*'
   * @return true if this is a hint comment
   */
  private boolean isHintComment(char[] chars, int startPos) {
    // Check for /*+ pattern (hint must have + immediately after /*)
    if (startPos + 2 < chars.length && chars[startPos + 2] == '+') {
      return true;
    }
    return false;
  }

  /**
   * Finds the end position of a single-line comment.
   *
   * @param chars the SQL character array
   * @param startPos the position after the comment marker
   * @return the position after the newline or end of string
   */
  private int findLineEnd(char[] chars, int startPos) {
    for (int i = startPos; i < chars.length; i++) {
      if (chars[i] == '\n' || chars[i] == '\r') {
        return i + 1;
      }
    }
    return chars.length;
  }

  /**
   * Finds the end position of a block comment.
   *
   * @param chars the SQL character array
   * @param startPos the position after '/*'
   * @return the position after '*\/' or end of string if unclosed
   */
  private int findBlockCommentEnd(char[] chars, int startPos) {
    for (int i = startPos; i < chars.length - 1; i++) {
      if (chars[i] == '*' && chars[i + 1] == '/') {
        return i + 2;
      }
    }
    return chars.length; // Unclosed comment
  }

  /**
   * Finds the position of the closing brace for MyBatis parameter syntax.
   *
   * @param chars the SQL character array
   * @param startPos the position after '#{'
   * @return the position of '}' or -1 if not found
   */
  private int findClosingBrace(char[] chars, int startPos) {
    for (int i = startPos; i < chars.length; i++) {
      if (chars[i] == '}') {
        return i;
      }
      // MyBatis parameters shouldn't contain newlines
      if (chars[i] == '\n' || chars[i] == '\r') {
        return -1;
      }
    }
    return -1; // Not found
  }

  /**
   * Filters out hint comments if they are allowed by configuration.
   *
   * <p>When allowHintComments=true, Oracle optimizer hints are permitted.
   * Additionally, we verify the hint is recognized by JSqlParser's getOracleHint() method.</p>
   *
   * @param comments the list of detected comments
   * @param context the SQL context containing the parsed statement
   * @return filtered list with hint comments removed
   */
  private List<CommentInfo> filterHintComments(List<CommentInfo> comments, SqlContext context) {
    List<CommentInfo> filtered = new ArrayList<>();

    for (CommentInfo comment : comments) {
      // Keep non-hint comments
      if (comment.type != CommentType.HINT) {
        filtered.add(comment);
        continue;
      }

      // For hint comments, verify they are recognized by JSqlParser
      // This ensures we only allow legitimate hints, not disguised malicious comments
      if (!isRecognizedHint(context)) {
        filtered.add(comment);
      }
      // If recognized, filter out (allow) the hint
    }

    return filtered;
  }

  /**
   * Checks if the statement has a recognized Oracle hint.
   *
   * <p>JSqlParser preserves Oracle hints in getOracleHint() method for SELECT, UPDATE, DELETE.</p>
   *
   * @param context the SQL context
   * @return true if a recognized hint exists
   */
  private boolean isRecognizedHint(SqlContext context) {
    Statement stmt = context.getStatement();
    if (stmt == null) {
      return false;
    }

    try {
      if (stmt instanceof Select) {
        Select select = (Select) stmt;
        SelectBody body = select.getSelectBody();
        if (body instanceof PlainSelect) {
          PlainSelect ps = (PlainSelect) body;
          return ps.getOracleHint() != null;
        }
      } else if (stmt instanceof Update) {
        Update update = (Update) stmt;
        return update.getOracleHint() != null;
      } else if (stmt instanceof Delete) {
        Delete delete = (Delete) stmt;
        return delete.getOracleHint() != null;
      }
    } catch (Exception e) {
      logger.debug("Error checking Oracle hint: {}", e.getMessage());
    }

    return false;
  }

  /**
   * Builds a violation message for the detected comment.
   *
   * @param comment the detected comment info
   * @return the violation message
   */
  private String buildViolationMessage(CommentInfo comment) {
    String typeDesc;
    switch (comment.type) {
      case SINGLE_LINE:
        typeDesc = "单行注释(--)";
        break;
      case MULTI_LINE:
        typeDesc = "多行注释(/* */)";
        break;
      case HINT:
        typeDesc = "优化器提示(/*+ */)";
        break;
      case MYSQL_HASH:
        typeDesc = "MySQL注释(#)";
        break;
      default:
        typeDesc = "未知注释";
    }

    // Truncate long comments for display
    String preview = comment.content;
    if (preview.length() > 50) {
      preview = preview.substring(0, 47) + "...";
    }

    return String.format("检测到SQL注释: %s (可能存在SQL注入风险) - 位置: %d, 内容: %s",
        typeDesc, comment.startPos, preview);
  }

  /**
   * Builds a suggestion for fixing the detected comment.
   *
   * @param comment the detected comment info
   * @return the suggestion message
   */
  private String buildSuggestion(CommentInfo comment) {
    switch (comment.type) {
      case SINGLE_LINE:
        return "移除SQL中的单行注释(--)，注释可能被用于绕过安全检测或隐藏恶意代码";
      case MULTI_LINE:
        return "移除SQL中的多行注释(/* */)，注释可能被用于绕过安全检测或隐藏恶意代码";
      case HINT:
        return "如需使用Oracle优化器提示，请在配置中设置allowHintComments=true";
      case MYSQL_HASH:
        return "移除SQL中的MySQL注释(#)，注释可能被用于绕过安全检测或隐藏恶意代码";
      default:
        return "移除SQL中的注释以提高安全性";
    }
  }

  /**
   * Comment type enumeration.
   */
  private enum CommentType {
    /** Single-line comment (--) */
    SINGLE_LINE,
    /** Multi-line comment (/* * /) */
    MULTI_LINE,
    /** Oracle optimizer hint (/*+ * /) */
    HINT,
    /** MySQL hash comment (#) */
    MYSQL_HASH
  }

  /**
   * Information about a detected comment.
   */
  private static class CommentInfo {
    final CommentType type;
    final int startPos;
    final int endPos;
    final String content;

    CommentInfo(CommentType type, int startPos, int endPos, String content) {
      this.type = type;
      this.startPos = startPos;
      this.endPos = endPos;
      this.content = content;
    }
  }
}
