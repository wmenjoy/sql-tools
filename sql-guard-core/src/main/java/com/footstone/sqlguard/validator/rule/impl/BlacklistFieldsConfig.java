package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for BlacklistFieldChecker.
 *
 * <p>Defines a set of blacklisted field names that should not be used alone in WHERE clauses.
 * Blacklisted fields are typically low-cardinality state flags (deleted, status, enabled, etc.)
 * that cause excessive row matches and near-full-table scans when used as the only WHERE
 * condition.</p>
 *
 * <p><strong>Default Blacklist:</strong></p>
 * <ul>
 *   <li>deleted - Soft delete flag</li>
 *   <li>del_flag - Alternative soft delete flag</li>
 *   <li>status - Generic status field</li>
 *   <li>is_deleted - Another soft delete variant</li>
 *   <li>enabled - Enable/disable flag</li>
 *   <li>type - Generic type field</li>
 * </ul>
 *
 * <p><strong>Wildcard Support:</strong></p>
 * <p>Blacklist entries ending with '*' are treated as prefix patterns. For example:</p>
 * <ul>
 *   <li>"create_*" matches: create_time, create_by, create_date</li>
 *   <li>"update_*" matches: update_time, update_by, update_date</li>
 * </ul>
 *
 * <p><strong>Case Sensitivity:</strong></p>
 * <p>All matching is case-insensitive. "DELETED", "deleted", and "Deleted" are treated
 * identically.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Use default blacklist
 * BlacklistFieldsConfig config = new BlacklistFieldsConfig();
 *
 * // Custom blacklist
 * Set<String> customBlacklist = new HashSet<>(Arrays.asList("is_active", "flag", "create_*"));
 * BlacklistFieldsConfig config = new BlacklistFieldsConfig(true, customBlacklist);
 * }</pre>
 */
public class BlacklistFieldsConfig extends CheckerConfig {

  /**
   * Default blacklist fields - common low-cardinality state flags.
   */
  private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
      "deleted",
      "del_flag",
      "status",
      "is_deleted",
      "enabled",
      "type"
  ));

  /**
   * Set of blacklisted field names (supports wildcards with * suffix).
   * Note: Marked as non-final to support YAML deserialization via setter.
   * After construction, use getFields() which returns an unmodifiable view.
   */
  private Set<String> fields;

  /**
   * Creates a BlacklistFieldsConfig with default settings.
   * <ul>
   *   <li>enabled = true</li>
   *   <li>fields = default blacklist (deleted, del_flag, status, is_deleted, enabled, type)</li>
   * </ul>
   */
  public BlacklistFieldsConfig() {
    super();
    this.fields = new HashSet<>(DEFAULT_BLACKLIST);
  }

  /**
   * Creates a BlacklistFieldsConfig with specified enabled state and default blacklist.
   *
   * @param enabled whether the checker should be enabled
   */
  public BlacklistFieldsConfig(boolean enabled) {
    super(enabled);
    this.fields = new HashSet<>(DEFAULT_BLACKLIST);
  }

  /**
   * Creates a BlacklistFieldsConfig with specified enabled state and custom blacklist.
   *
   * @param enabled whether the checker should be enabled
   * @param fields custom set of blacklisted field names (supports wildcards)
   */
  public BlacklistFieldsConfig(boolean enabled, Set<String> fields) {
    super(enabled);
    this.fields = fields != null ? new HashSet<>(fields) : new HashSet<>(DEFAULT_BLACKLIST);
  }

  /**
   * Returns an unmodifiable view of the blacklisted field names.
   *
   * <p>Field names may include wildcard patterns (e.g., "create_*"). Matching is
   * case-insensitive.</p>
   *
   * @return unmodifiable set of blacklisted field names
   */
  public Set<String> getFields() {
    return java.util.Collections.unmodifiableSet(fields);
  }

  /**
   * Sets the blacklisted field names.
   *
   * @param fields set of blacklisted field names (supports wildcards)
   */
  public void setFields(Set<String> fields) {
    this.fields = fields != null ? new HashSet<>(fields) : new HashSet<>(DEFAULT_BLACKLIST);
  }
}
