package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;

/**
 * Rule checker that detects WHERE conditions using only blacklisted fields.
 *
 * <p>Blacklisted fields are typically low-cardinality state flags (deleted, status, enabled, etc.)
 * that cause excessive row matches and near-full-table scans when used as the only WHERE
 * condition. Such queries appear to have WHERE clauses but still match most rows in the table,
 * resulting in poor performance.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Extract WHERE clause from statement</li>
 *   <li>Extract all field names from WHERE clause</li>
 *   <li>Check if ALL fields are blacklisted</li>
 *   <li>If true, add HIGH risk violation</li>
 * </ol>
 *
 * <p><strong>Risk Level:</strong> HIGH</p>
 * <p>Queries with blacklist-only conditions appear to filter data but still scan most rows,
 * causing performance issues similar to missing WHERE clauses.</p>
 *
 * <p><strong>Examples:</strong></p>
 * <pre>{@code
 * // VIOLATES - only blacklisted field
 * SELECT * FROM users WHERE deleted=0
 * SELECT * FROM orders WHERE status='active'
 * SELECT * FROM products WHERE enabled=1 AND type='normal'
 *
 * // PASSES - includes non-blacklisted field
 * SELECT * FROM users WHERE id=1 AND deleted=0
 * SELECT * FROM orders WHERE user_id=? AND status='active'
 * }</pre>
 *
 * <p><strong>Wildcard Support:</strong></p>
 * <p>Blacklist entries ending with '*' are treated as prefix patterns:</p>
 * <pre>{@code
 * // If blacklist includes "create_*"
 * SELECT * FROM users WHERE create_time > ?  // VIOLATES
 * SELECT * FROM users WHERE create_by = ?    // VIOLATES
 * }</pre>
 *
 * <p><strong>Configuration:</strong></p>
 * <ul>
 *   <li>Default blacklist: deleted, del_flag, status, is_deleted, enabled, type</li>
 *   <li>Custom blacklist via {@link BlacklistFieldsConfig}</li>
 *   <li>Wildcard patterns supported (e.g., "create_*", "update_*")</li>
 *   <li>Case-insensitive matching</li>
 * </ul>
 *
 * @see BlacklistFieldsConfig
 * @see AbstractRuleChecker
 */
public class BlacklistFieldChecker extends AbstractRuleChecker {

  private final BlacklistFieldsConfig config;

  /**
   * Creates a BlacklistFieldChecker with the specified configuration.
   *
   * @param config the configuration defining blacklisted fields
   */
  public BlacklistFieldChecker(BlacklistFieldsConfig config) {
    this.config = config;
  }

  /**
   * Checks if the SQL statement's WHERE clause uses only blacklisted fields.
   *
   * <p>Skips validation if:</p>
   * <ul>
   *   <li>Checker is disabled</li>
   *   <li>No WHERE clause present (handled by NoWhereClauseChecker)</li>
   *   <li>WHERE clause contains at least one non-blacklisted field</li>
   * </ul>
   *
   * @param context the SQL context containing the parsed statement
   * @param result the validation result to populate with violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    // Skip if checker is disabled
    if (!config.isEnabled()) {
      return;
    }

    Statement stmt = context.getParsedSql();

    // Extract WHERE clause
    Expression where = extractWhere(stmt);

    // Skip if no WHERE clause (handled by NoWhereClauseChecker)
    if (where == null) {
      return;
    }

    // Extract all field names from WHERE clause
    Set<String> whereFields = extractFields(where);

    // Skip if no fields found (e.g., WHERE 1=1)
    if (whereFields.isEmpty()) {
      return;
    }

    // Check if ALL fields are blacklisted
    boolean allFieldsBlacklisted = whereFields.stream()
        .allMatch(field -> isBlacklisted(field, config.getFields()));

    // Add violation if all fields are blacklisted
    if (allFieldsBlacklisted) {
      String fieldList = whereFields.stream()
          .collect(Collectors.joining(", "));

      String message = String.format(
          "WHERE条件只包含黑名单字段[%s],条件过于宽泛",
          fieldList
      );

      String suggestion = "添加主键或业务唯一键字段(如id, user_id)";

      result.addViolation(RiskLevel.HIGH, message, suggestion);
    }
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
   * Checks if a field name matches any entry in the blacklist.
   *
   * <p>Matching rules:</p>
   * <ul>
   *   <li>Case-insensitive comparison</li>
   *   <li>Exact match: "deleted" matches "deleted"</li>
   *   <li>Wildcard match: "create_*" matches "create_time", "create_by", etc.</li>
   * </ul>
   *
   * @param field the field name to check
   * @param blacklist the set of blacklisted patterns
   * @return true if the field is blacklisted, false otherwise
   */
  private boolean isBlacklisted(String field, Set<String> blacklist) {
    if (field == null || blacklist == null || blacklist.isEmpty()) {
      return false;
    }

    String fieldLower = field.toLowerCase();

    for (String blacklistEntry : blacklist) {
      String entryLower = blacklistEntry.toLowerCase();

      // Check for exact match
      if (fieldLower.equals(entryLower)) {
        return true;
      }

      // Check for wildcard pattern (e.g., "create_*")
      if (entryLower.endsWith("*")) {
        String prefix = entryLower.substring(0, entryLower.length() - 1);
        if (fieldLower.startsWith(prefix)) {
          return true;
        }
      }
    }

    return false;
  }
}








