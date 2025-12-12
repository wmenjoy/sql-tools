package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import java.util.List;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;

/**
 * Rule checker that enforces table-specific mandatory WHERE fields (whitelist).
 *
 * <p>WhitelistFieldChecker ensures queries include primary keys, tenant IDs, or other
 * high-selectivity fields for critical tables, providing an additional safety layer.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Table-specific whitelist enforcement (any one required field must be present)</li>
 *   <li>Optional global whitelist for unknown tables</li>
 *   <li>MEDIUM risk level (less severe than blacklist-only, opt-in enforcement)</li>
 *   <li>Useful for multi-tenant systems, GDPR compliance, critical business tables</li>
 * </ul>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * WhitelistFieldsConfig config = new WhitelistFieldsConfig();
 * 
 * // Setup table-specific whitelist
 * Map<String, List<String>> byTable = new HashMap<>();
 * byTable.put("user", Arrays.asList("id", "user_id"));
 * byTable.put("tenant_data", Arrays.asList("tenant_id"));
 * config.setByTable(byTable);
 * 
 * WhitelistFieldChecker checker = new WhitelistFieldChecker(config);
 * }</pre>
 *
 * <p><strong>Validation Logic:</strong></p>
 * <ol>
 *   <li>Extract table name from statement</li>
 *   <li>Lookup required fields from byTable map</li>
 *   <li>If table not in map:
 *     <ul>
 *       <li>If enforceForUnknownTables=false, skip validation</li>
 *       <li>If enforceForUnknownTables=true, use global fields</li>
 *     </ul>
 *   </li>
 *   <li>Extract WHERE fields and check if ANY required field is present</li>
 *   <li>If no required field found, add MEDIUM violation</li>
 * </ol>
 *
 * @see WhitelistFieldsConfig
 * @see AbstractRuleChecker
 */
public class WhitelistFieldChecker extends AbstractRuleChecker {

  private final WhitelistFieldsConfig config;

  /**
   * Creates a WhitelistFieldChecker with the specified configuration.
   *
   * @param config the whitelist configuration
   */
  public WhitelistFieldChecker(WhitelistFieldsConfig config) {
    this.config = config;
  }

  /**
   * Checks if the SQL statement includes required whitelist fields in WHERE clause.
   *
   * <p>Validates that queries include at least one required field from the table-specific
   * whitelist. Tables not in the whitelist map are skipped unless enforceForUnknownTables=true.</p>
   *
   * @param context the SQL context containing parsed statement
   * @param result the validation result to populate with violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (!config.isEnabled()) {
      return;
    }

    Statement stmt = context.getParsedSql();
    if (stmt == null) {
      return;
    }

    // Extract table name
    String tableName = extractTableName(stmt);
    if (tableName == null) {
      return;
    }

    // Extract WHERE clause
    Expression where = extractWhere(stmt);
    if (where == null) {
      // No WHERE clause means nothing to check
      return;
    }

    // Lookup required fields for this table
    List<String> requiredFields = config.getByTable().get(tableName);

    // If table not in whitelist map
    if (requiredFields == null) {
      if (!config.isEnforceForUnknownTables()) {
        // Skip validation for unknown tables
        return;
      }
      // Use global fields for unknown tables
      requiredFields = config.getFields();
    }

    // If no required fields (empty list), skip validation
    if (requiredFields == null || requiredFields.isEmpty()) {
      return;
    }

    // Extract fields from WHERE clause
    Set<String> whereFields = extractFields(where);

    // Check if ANY required field is present
    boolean hasRequiredField = whereFields.stream()
        .anyMatch(requiredFields::contains);

    if (!hasRequiredField) {
      // Add MEDIUM violation
      String message = "表" + tableName + "的WHERE条件必须包含以下字段之一:" + requiredFields;
      String suggestion = "添加主键或业务唯一键字段到WHERE条件";
      result.addViolation(RiskLevel.MEDIUM, message, suggestion);
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
}

