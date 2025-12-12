package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.session.RowBounds;

/**
 * Checker for detecting SELECT queries completely lacking pagination limits.
 *
 * <p>This checker identifies queries without any form of pagination (no LIMIT clause, no
 * RowBounds, no IPage parameter) and applies variable risk stratification based on WHERE
 * clause characteristics:</p>
 *
 * <p><strong>Risk Stratification:</strong></p>
 * <ul>
 *   <li><strong>CRITICAL:</strong> No WHERE clause or dummy WHERE (e.g., "1=1") - returns entire
 *       table, causing memory overflow risk on large tables</li>
 *   <li><strong>HIGH:</strong> WHERE clause uses ONLY blacklist fields (e.g., "WHERE deleted=0")
 *       - returns most rows with minimal filtering effect</li>
 *   <li><strong>MEDIUM:</strong> Normal WHERE clause with business fields, but only when
 *       enforceForAllQueries=true - preventive measure for consistency</li>
 * </ul>
 *
 * <p><strong>Whitelist Exemptions:</strong></p>
 * <ul>
 *   <li>Mapper ID patterns (e.g., "*.getById", "*.count*")</li>
 *   <li>Table whitelist (e.g., config tables, system tables)</li>
 *   <li>Unique key equality conditions (e.g., "WHERE id=?") - guarantees single-row result</li>
 * </ul>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <pre>
 * 1. Skip if checker disabled
 * 2. Skip if not SELECT statement
 * 3. Check for pagination (LIMIT/RowBounds/IPage) - skip if present
 * 4. Check whitelist exemptions - skip if whitelisted
 * 5. Assess risk based on WHERE clause and add violation
 * </pre>
 *
 * @see NoPaginationConfig
 * @see PaginationPluginDetector
 */
public class NoPaginationChecker extends AbstractRuleChecker {

  private final PaginationPluginDetector pluginDetector;
  private final BlacklistFieldsConfig blacklistConfig;
  private final NoPaginationConfig config;

  /**
   * Constructs a NoPaginationChecker with required dependencies.
   *
   * @param pluginDetector detector for pagination plugins (MyBatis PageHelper, MyBatis-Plus)
   * @param blacklistConfig configuration for blacklist field detection
   * @param config configuration for this checker
   */
  public NoPaginationChecker(PaginationPluginDetector pluginDetector,
      BlacklistFieldsConfig blacklistConfig, NoPaginationConfig config) {
    this.pluginDetector = pluginDetector;
    this.blacklistConfig = blacklistConfig;
    this.config = config;
  }

  /**
   * Checks if the SQL query lacks pagination and assesses risk level.
   *
   * <p>Detection flow:</p>
   * <ol>
   *   <li>Skip if checker disabled</li>
   *   <li>Skip if not SELECT statement</li>
   *   <li>Check for pagination (LIMIT/RowBounds/IPage) - skip if present</li>
   *   <li>Check whitelist exemptions - skip if whitelisted</li>
   *   <li>Assess risk based on WHERE clause and add violation</li>
   * </ol>
   *
   * @param context SQL execution context
   * @param result validation result to accumulate violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    // Step 1: Skip if checker disabled
    if (!isEnabled()) {
      return;
    }

    // Step 2: Skip if not SELECT
    Statement stmt = context.getParsedSql();
    if (!(stmt instanceof Select)) {
      return;
    }

    Select select = (Select) stmt;

    // Step 3: Check for pagination
    if (hasPaginationLimit(select, context)) {
      return;
    }

    // Step 4: Check whitelist exemptions
    if (isWhitelisted(select, context)) {
      return;
    }

    // Step 5: Assess risk and add violation
    assessNoPaginationRisk(select, context, result);
  }

  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }

  /**
   * Checks if any form of pagination is present.
   *
   * <p>Returns true if any of the following conditions are met:</p>
   * <ul>
   *   <li>SQL has LIMIT clause</li>
   *   <li>RowBounds parameter exists (not null, not DEFAULT) - treated as pagination attempt</li>
   *   <li>IPage parameter exists - treated as pagination attempt</li>
   * </ul>
   *
   * <p>Note: RowBounds/IPage without plugin will be caught by LogicalPaginationChecker,
   * so we treat them as "pagination present" to avoid double-reporting.</p>
   *
   * @param select the SELECT statement
   * @param context SQL execution context
   * @return true if pagination is present, false otherwise
   */
  private boolean hasPaginationLimit(Select select, SqlContext context) {
    // Check SQL LIMIT clause
    String sql = select.toString().toUpperCase();
    if (sql.contains("LIMIT")) {
      return true;
    }

    // Check RowBounds (MyBatis pagination parameter)
    // Treat RowBounds as pagination attempt even without plugin
    // (LogicalPaginationChecker will handle the case without plugin)
    Object rowBounds = context.getRowBounds();
    if (rowBounds != null && rowBounds instanceof RowBounds) {
      RowBounds rb = (RowBounds) rowBounds;
      // RowBounds.DEFAULT is infinite bounds (not pagination)
      if (rb != RowBounds.DEFAULT) {
        return true;
      }
    }

    // Check IPage parameter (MyBatis-Plus pagination parameter)
    // Treat IPage as pagination attempt even without plugin
    if (hasPageParameter(context.getParams())) {
      return true;
    }

    return false;
  }

  /**
   * Checks if any parameter is an IPage instance.
   *
   * @param params parameter map from SqlContext
   * @return true if any parameter is IPage, false otherwise
   */
  private boolean hasPageParameter(Map<String, Object> params) {
    if (params == null || params.isEmpty()) {
      return false;
    }

    for (Object value : params.values()) {
      if (value != null) {
        String className = value.getClass().getName();
        if (className.contains("IPage") 
            || className.contains("com.baomidou.mybatisplus.core.metadata.IPage")) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if the query should be exempt from check.
   *
   * <p>Returns true if any of the following conditions are met:</p>
   * <ul>
   *   <li>Mapper ID matches whitelist pattern (supports wildcards)</li>
   *   <li>Table name is in whitelist</li>
   *   <li>WHERE clause contains unique key equality condition</li>
   * </ul>
   *
   * @param select the SELECT statement
   * @param context SQL execution context
   * @return true if whitelisted, false otherwise
   */
  private boolean isWhitelisted(Select select, SqlContext context) {
    // Check mapperId whitelist
    String mapperId = context.getMapperId();
    if (mapperId != null && matchesWildcardPattern(mapperId, config.getWhitelistMapperIds())) {
      return true;
    }

    // Check table whitelist
    String tableName = extractTableName(select);
    if (tableName != null && config.getWhitelistTables().contains(tableName)) {
      return true;
    }

    // Check unique key condition
    if (hasUniqueKeyCondition(select, context)) {
      return true;
    }

    return false;
  }

  /**
   * Checks if WHERE clause contains unique key equality condition.
   *
   * <p>Returns true if WHERE contains an equality condition on a unique key field
   * (default "id" or custom unique keys from config) with a constant or parameter value.</p>
   *
   * @param select the SELECT statement
   * @param context SQL execution context
   * @return true if unique key equality found, false otherwise
   */
  private boolean hasUniqueKeyCondition(Select select, SqlContext context) {
    // Extract WHERE clause
    Expression where = extractWhere(select);
    if (where == null) {
      return false;
    }

    // Build unique key set: "id" + custom unique keys
    Set<String> uniqueKeys = new HashSet<>();
    uniqueKeys.add("id");
    for (String key : config.getUniqueKeyFields()) {
      uniqueKeys.add(key.toLowerCase());
    }

    // Use visitor to find unique key equality
    UniqueKeyVisitor visitor = new UniqueKeyVisitor(uniqueKeys);
    where.accept(visitor);
    return visitor.isFoundUniqueKeyEquals();
  }

  /**
   * Assesses risk level based on WHERE clause characteristics and adds violation.
   *
   * <p>Risk stratification:</p>
   * <ul>
   *   <li><strong>CRITICAL:</strong> No WHERE or dummy WHERE (e.g., "1=1")</li>
   *   <li><strong>HIGH:</strong> WHERE uses ONLY blacklist fields</li>
   *   <li><strong>MEDIUM:</strong> Normal WHERE with business fields (only if
   *       enforceForAllQueries=true)</li>
   * </ul>
   *
   * @param select the SELECT statement
   * @param context SQL execution context
   * @param result validation result to add violation
   */
  private void assessNoPaginationRisk(Select select, SqlContext context, ValidationResult result) {
    Expression where = extractWhere(select);

    // CRITICAL: No WHERE or dummy WHERE
    if (where == null || isDummyCondition(where)) {
      result.addViolation(
          RiskLevel.CRITICAL,
          "SELECT查询无条件且无分页限制,可能返回全表数据导致内存溢出",
          "添加WHERE条件和分页限制(LIMIT或RowBounds)");
      return;
    }

    // Extract fields from WHERE clause
    Set<String> whereFields = extractFields(where);

    // HIGH: WHERE uses ONLY blacklist fields
    if (!whereFields.isEmpty() && allFieldsBlacklisted(whereFields, blacklistConfig)) {
      String fieldsStr = String.join(", ", whereFields);
      result.addViolation(
          RiskLevel.HIGH,
          String.format("SELECT查询条件只有黑名单字段[%s]且无分页,可能返回大量数据", fieldsStr),
          "添加业务字段条件或分页限制");
      return;
    }

    // MEDIUM: Normal WHERE (only if enforceForAllQueries=true)
    if (config.isEnforceForAllQueries()) {
      result.addViolation(
          RiskLevel.MEDIUM,
          "SELECT查询缺少分页限制,建议添加LIMIT或使用分页",
          "为避免潜在性能问题,建议添加分页");
    }
  }

  /**
   * Checks if all WHERE fields are in blacklist.
   *
   * @param whereFields fields extracted from WHERE clause
   * @param blacklistConfig blacklist configuration
   * @return true if all fields are blacklisted, false otherwise
   */
  private boolean allFieldsBlacklisted(Set<String> whereFields, 
      BlacklistFieldsConfig blacklistConfig) {
    if (whereFields.isEmpty()) {
      return false;
    }

    Set<String> blacklist = blacklistConfig.getFields();
    
    for (String field : whereFields) {
      String fieldLower = field.toLowerCase();
      boolean isBlacklisted = false;

      // Check exact match or wildcard pattern
      for (String blacklistPattern : blacklist) {
        String patternLower = blacklistPattern.toLowerCase();
        
        // Exact match
        if (fieldLower.equals(patternLower)) {
          isBlacklisted = true;
          break;
        }
        
        // Wildcard pattern (e.g., "create_*")
        if (patternLower.endsWith("*")) {
          String prefix = patternLower.substring(0, patternLower.length() - 1);
          if (fieldLower.startsWith(prefix)) {
            isBlacklisted = true;
            break;
          }
        }
      }

      // If any field is NOT blacklisted, return false
      if (!isBlacklisted) {
        return false;
      }
    }

    // All fields are blacklisted
    return true;
  }

  /**
   * Checks if text matches any wildcard pattern in the list.
   *
   * <p>Supports wildcard patterns with * (e.g., "*.getById", "*.count*", "ConfigMapper.*").</p>
   *
   * @param text text to match
   * @param patterns list of patterns (may contain wildcards)
   * @return true if text matches any pattern, false otherwise
   */
  private boolean matchesWildcardPattern(String text, List<String> patterns) {
    if (text == null || patterns == null || patterns.isEmpty()) {
      return false;
    }

    for (String pattern : patterns) {
      if (pattern == null) {
        continue;
      }

      // Exact match
      if (text.equals(pattern)) {
        return true;
      }

      // Wildcard pattern: convert * to .* for regex
      String regex = pattern.replace(".", "\\.").replace("*", ".*");
      if (text.matches(regex)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Visitor that detects unique key equality conditions in WHERE clause.
   *
   * <p>Traverses the expression tree and looks for EqualsTo expressions where:</p>
   * <ul>
   *   <li>Left side is a Column with name in uniqueKeys set</li>
   *   <li>Right side is a constant (LongValue, StringValue) or parameter (JdbcParameter)</li>
   * </ul>
   */
  private static class UniqueKeyVisitor extends ExpressionVisitorAdapter {

    private boolean foundUniqueKeyEquals = false;
    private final Set<String> uniqueKeys;

    /**
     * Constructs a UniqueKeyVisitor with unique key set.
     *
     * @param uniqueKeys set of unique key field names (lowercase)
     */
    public UniqueKeyVisitor(Set<String> uniqueKeys) {
      this.uniqueKeys = uniqueKeys;
    }

    /**
     * Returns whether unique key equality was found.
     *
     * @return true if found, false otherwise
     */
    public boolean isFoundUniqueKeyEquals() {
      return foundUniqueKeyEquals;
    }

    @Override
    public void visit(EqualsTo equalsTo) {
      Expression left = equalsTo.getLeftExpression();
      Expression right = equalsTo.getRightExpression();

      // Check if left is a Column and in uniqueKeys
      if (left instanceof Column) {
        String columnName = ((Column) left).getColumnName().toLowerCase();
        if (uniqueKeys.contains(columnName)) {
          // Check if right is constant or parameter (not another column)
          if (right instanceof JdbcParameter 
              || right instanceof LongValue 
              || right instanceof StringValue) {
            foundUniqueKeyEquals = true;
          }
        }
      }
    }
  }
}

