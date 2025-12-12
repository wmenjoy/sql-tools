package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for WhitelistFieldChecker.
 *
 * <p>Defines table-specific mandatory WHERE fields (whitelist) to ensure queries include
 * primary keys, tenant IDs, or other high-selectivity fields for critical tables.</p>
 *
 * <p><strong>Configuration Fields:</strong></p>
 * <ul>
 *   <li>{@code fields} - Global whitelist (optional, used when enforceForUnknownTables=true)</li>
 *   <li>{@code byTable} - Table-specific whitelist map (key=table name, value=required fields)</li>
 *   <li>{@code enforceForUnknownTables} - Whether to enforce global whitelist for unknown tables</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * WhitelistFieldsConfig config = new WhitelistFieldsConfig();
 * 
 * // Setup table-specific whitelist
 * Map<String, List<String>> byTable = new HashMap<>();
 * byTable.put("user", Arrays.asList("id", "user_id"));
 * byTable.put("tenant_data", Arrays.asList("tenant_id"));
 * config.setByTable(byTable);
 * 
 * // Optional: enforce global whitelist for unknown tables
 * config.setFields(Arrays.asList("id", "tenant_id"));
 * config.setEnforceForUnknownTables(true);
 * }</pre>
 *
 * @see WhitelistFieldChecker
 */
public class WhitelistFieldsConfig extends CheckerConfig {

  /**
   * Global whitelist fields (optional, default empty).
   * Used when enforceForUnknownTables=true for tables not in byTable map.
   */
  private List<String> fields;

  /**
   * Table-specific whitelist map.
   * Key: table name
   * Value: list of required fields (any one must be present in WHERE clause)
   */
  private Map<String, List<String>> byTable;

  /**
   * Whether to enforce global whitelist for unknown tables (default: false).
   * If true, tables not in byTable map must include one of the global fields.
   * If false, tables not in byTable map are skipped.
   */
  private boolean enforceForUnknownTables;

  /**
   * Creates a WhitelistFieldsConfig with default settings (enabled=true).
   */
  public WhitelistFieldsConfig() {
    super();
    this.fields = new ArrayList<>();
    this.byTable = new HashMap<>();
    this.enforceForUnknownTables = false;
  }

  /**
   * Creates a WhitelistFieldsConfig with specified enabled state.
   *
   * @param enabled whether the checker should be enabled
   */
  public WhitelistFieldsConfig(boolean enabled) {
    super(enabled);
    this.fields = new ArrayList<>();
    this.byTable = new HashMap<>();
    this.enforceForUnknownTables = false;
  }

  /**
   * Returns the global whitelist fields.
   *
   * @return list of global required fields
   */
  public List<String> getFields() {
    return fields;
  }

  /**
   * Sets the global whitelist fields.
   *
   * @param fields list of global required fields
   */
  public void setFields(List<String> fields) {
    this.fields = fields;
  }

  /**
   * Returns the table-specific whitelist map.
   *
   * @return map of table name to required fields
   */
  public Map<String, List<String>> getByTable() {
    return byTable;
  }

  /**
   * Sets the table-specific whitelist map.
   *
   * @param byTable map of table name to required fields
   */
  public void setByTable(Map<String, List<String>> byTable) {
    this.byTable = byTable;
  }

  /**
   * Returns whether to enforce global whitelist for unknown tables.
   *
   * @return true if unknown tables should be enforced, false otherwise
   */
  public boolean isEnforceForUnknownTables() {
    return enforceForUnknownTables;
  }

  /**
   * Sets whether to enforce global whitelist for unknown tables.
   *
   * @param enforceForUnknownTables true to enforce, false to skip unknown tables
   */
  public void setEnforceForUnknownTables(boolean enforceForUnknownTables) {
    this.enforceForUnknownTables = enforceForUnknownTables;
  }
}

