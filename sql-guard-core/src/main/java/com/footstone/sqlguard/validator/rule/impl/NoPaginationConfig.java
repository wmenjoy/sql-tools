package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for NoPaginationChecker.
 *
 * <p>Defines configuration for detecting SELECT queries completely lacking pagination limits
 * (no LIMIT, no RowBounds, no IPage), with variable risk stratification based on WHERE clause
 * characteristics, whitelist exemptions for known-safe queries, and unique key detection for
 * single-row queries.</p>
 *
 * <p><strong>Risk Stratification Logic:</strong></p>
 * <ul>
 *   <li><strong>CRITICAL:</strong> No WHERE clause or dummy WHERE (e.g., "1=1") - returns entire
 *       table, causing memory overflow risk on large tables</li>
 *   <li><strong>HIGH:</strong> WHERE clause uses ONLY blacklist fields (e.g., "WHERE deleted=0")
 *       - returns most rows with minimal filtering effect</li>
 *   <li><strong>MEDIUM:</strong> Normal WHERE clause with business fields, but only when
 *       enforceForAllQueries=true - preventive measure for consistency</li>
 *   <li><strong>SAFE:</strong> Query has pagination (LIMIT/RowBounds/IPage) or matches whitelist
 *       exemptions</li>
 * </ul>
 *
 * <p><strong>Whitelist Exemption Use Cases:</strong></p>
 * <ul>
 *   <li><strong>Mapper ID Patterns:</strong>
 *       <ul>
 *         <li>"*.getById" - Single-row getters (safe without pagination)</li>
 *         <li>"*.count*" - Count queries (aggregate, no row return)</li>
 *         <li>"ConfigMapper.*" - Config table queries (small tables)</li>
 *       </ul>
 *   </li>
 *   <li><strong>Table Whitelist:</strong> Config tables, system tables, lookup tables with
 *       limited rows</li>
 *   <li><strong>Unique Key Detection:</strong> Queries with unique key equality conditions
 *       (e.g., "WHERE id=?") guarantee single-row results</li>
 * </ul>
 *
 * <p><strong>Configuration Fields:</strong></p>
 * <ul>
 *   <li>{@code whitelistMapperIds} - Mapper ID patterns (supports wildcards like "*.getById")</li>
 *   <li>{@code whitelistTables} - Table names exempt from check</li>
 *   <li>{@code uniqueKeyFields} - Custom unique key fields beyond default "id"</li>
 *   <li>{@code enforceForAllQueries} - If true, enforce MEDIUM violation for all non-paginated
 *       queries regardless of WHERE clause</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Default configuration (only flag CRITICAL/HIGH risks)
 * NoPaginationConfig config = new NoPaginationConfig();
 *
 * // Custom configuration with whitelists
 * NoPaginationConfig config = new NoPaginationConfig(
 *     true,                                    // enabled
 *     Arrays.asList("*.getById", "*.count*"), // whitelistMapperIds
 *     Arrays.asList("config_table"),          // whitelistTables
 *     Arrays.asList("user_id", "order_no"),   // uniqueKeyFields
 *     true                                     // enforceForAllQueries
 * );
 * }</pre>
 *
 * @see com.footstone.sqlguard.validator.pagination.impl.NoPaginationChecker
 */
public class NoPaginationConfig extends CheckerConfig {

  /**
   * Mapper ID patterns exempt from check (supports wildcards).
   * Examples: "*.getById", "*.count*", "ConfigMapper.*"
   */
  private List<String> whitelistMapperIds;

  /**
   * Table names exempt from check.
   * Examples: "config_table", "sys_dict", "country_codes"
   */
  private List<String> whitelistTables;

  /**
   * Custom unique key fields beyond default "id".
   * Examples: "user_id", "order_no", "uuid"
   */
  private List<String> uniqueKeyFields;

  /**
   * If true, enforce MEDIUM violation for all non-paginated queries.
   * If false (default), only flag CRITICAL (no WHERE) and HIGH (blacklist-only WHERE).
   */
  private boolean enforceForAllQueries;

  /**
   * Creates a NoPaginationConfig with default settings.
   * <ul>
   *   <li>enabled = true</li>
   *   <li>whitelistMapperIds = [] (empty, no exemptions)</li>
   *   <li>whitelistTables = [] (empty)</li>
   *   <li>uniqueKeyFields = [] (only "id" checked by default)</li>
   *   <li>enforceForAllQueries = false (only flag CRITICAL/HIGH)</li>
   * </ul>
   */
  public NoPaginationConfig() {
    super();
    this.whitelistMapperIds = new ArrayList<>();
    this.whitelistTables = new ArrayList<>();
    this.uniqueKeyFields = new ArrayList<>();
    this.enforceForAllQueries = false;
  }

  /**
   * Creates a NoPaginationConfig with all fields specified.
   *
   * @param enabled whether the checker should be enabled
   * @param whitelistMapperIds mapper ID patterns exempt from check (supports wildcards)
   * @param whitelistTables table names exempt from check
   * @param uniqueKeyFields custom unique key fields beyond "id"
   * @param enforceForAllQueries if true, enforce MEDIUM violation for all non-paginated queries
   */
  public NoPaginationConfig(boolean enabled, List<String> whitelistMapperIds,
      List<String> whitelistTables, List<String> uniqueKeyFields, boolean enforceForAllQueries) {
    super(enabled);
    this.whitelistMapperIds = whitelistMapperIds != null ? whitelistMapperIds : new ArrayList<>();
    this.whitelistTables = whitelistTables != null ? whitelistTables : new ArrayList<>();
    this.uniqueKeyFields = uniqueKeyFields != null ? uniqueKeyFields : new ArrayList<>();
    this.enforceForAllQueries = enforceForAllQueries;
  }

  /**
   * Returns the list of whitelisted mapper ID patterns.
   *
   * <p>Patterns support wildcards (e.g., "*.getById", "*.count*", "ConfigMapper.*").</p>
   *
   * @return list of mapper ID patterns
   */
  public List<String> getWhitelistMapperIds() {
    return whitelistMapperIds;
  }

  /**
   * Sets the list of whitelisted mapper ID patterns.
   *
   * @param whitelistMapperIds mapper ID patterns (supports wildcards)
   */
  public void setWhitelistMapperIds(List<String> whitelistMapperIds) {
    this.whitelistMapperIds = whitelistMapperIds != null ? whitelistMapperIds : new ArrayList<>();
  }

  /**
   * Returns the list of whitelisted table names.
   *
   * @return list of table names
   */
  public List<String> getWhitelistTables() {
    return whitelistTables;
  }

  /**
   * Sets the list of whitelisted table names.
   *
   * @param whitelistTables table names exempt from check
   */
  public void setWhitelistTables(List<String> whitelistTables) {
    this.whitelistTables = whitelistTables != null ? whitelistTables : new ArrayList<>();
  }

  /**
   * Returns the list of custom unique key fields.
   *
   * <p>These fields are in addition to the default "id" field. When a WHERE clause contains
   * an equality condition on any unique key field (e.g., "WHERE user_id=?"), the query is
   * exempt from pagination check as it guarantees single-row result.</p>
   *
   * @return list of unique key field names
   */
  public List<String> getUniqueKeyFields() {
    return uniqueKeyFields;
  }

  /**
   * Sets the list of custom unique key fields.
   *
   * @param uniqueKeyFields custom unique key fields beyond "id"
   */
  public void setUniqueKeyFields(List<String> uniqueKeyFields) {
    this.uniqueKeyFields = uniqueKeyFields != null ? uniqueKeyFields : new ArrayList<>();
  }

  /**
   * Returns whether to enforce MEDIUM violation for all non-paginated queries.
   *
   * <p>If true, all queries without pagination will trigger MEDIUM violation regardless of
   * WHERE clause (unless whitelisted). If false (default), only CRITICAL (no WHERE) and
   * HIGH (blacklist-only WHERE) violations are flagged.</p>
   *
   * @return true if enforcing for all queries, false otherwise
   */
  public boolean isEnforceForAllQueries() {
    return enforceForAllQueries;
  }

  /**
   * Sets whether to enforce MEDIUM violation for all non-paginated queries.
   *
   * @param enforceForAllQueries if true, enforce for all queries
   */
  public void setEnforceForAllQueries(boolean enforceForAllQueries) {
    this.enforceForAllQueries = enforceForAllQueries;
  }
}

