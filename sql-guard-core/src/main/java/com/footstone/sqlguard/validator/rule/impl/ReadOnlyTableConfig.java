package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for ReadOnlyTableChecker.
 *
 * <p>ReadOnlyTableConfig extends CheckerConfig to provide configuration for protecting
 * read-only tables from write operations (INSERT/UPDATE/DELETE). This is useful for
 * protecting audit logs, historical records, and reference data from accidental or
 * unauthorized modifications.</p>
 *
 * <p><strong>Wildcard Pattern Support:</strong></p>
 * <p>The readonlyTables list supports wildcard patterns using '*' at the end:</p>
 * <ul>
 *   <li><code>audit_log</code> - Exact match for table named "audit_log"</li>
 *   <li><code>history_*</code> - Matches all tables starting with "history_" (e.g., history_users, history_orders)</li>
 *   <li><code>archive_*</code> - Matches all tables starting with "archive_"</li>
 * </ul>
 *
 * <p><strong>Case Sensitivity:</strong></p>
 * <p>All matching is case-insensitive. "AUDIT_LOG", "Audit_Log", and "audit_log" are treated as equivalent.</p>
 *
 * <p><strong>Default Configuration:</strong></p>
 * <ul>
 *   <li>enabled: true</li>
 *   <li>violationStrategy: BLOCK (via default CheckerConfig behavior)</li>
 *   <li>readonlyTables: empty list (no tables protected by default)</li>
 *   <li>riskLevel: HIGH</li>
 * </ul>
 *
 * <p><strong>Example Configuration (YAML):</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     readonly-table:
 *       enabled: true
 *       readonly-tables:
 *         - audit_log
 *         - history_*
 *         - sys_config
 *         - reference_*
 * }</pre>
 *
 * <p><strong>Usage Scenarios:</strong></p>
 * <ul>
 *   <li><strong>Audit Logs:</strong> Protect audit_log, audit_events from modification (immutability requirement)</li>
 *   <li><strong>Historical Records:</strong> Protect history_* tables from modification (data integrity)</li>
 *   <li><strong>Reference Data:</strong> Protect lookup tables from accidental changes</li>
 *   <li><strong>System Configuration:</strong> Protect sys_* tables from unauthorized changes</li>
 * </ul>
 *
 * @see ReadOnlyTableChecker
 * @see CheckerConfig
 * @since 1.0.0
 */
public class ReadOnlyTableConfig extends CheckerConfig {

    /**
     * List of read-only table names or patterns.
     *
     * <p>Supports exact names and wildcard patterns ending with '*'.</p>
     * <p>Examples: "audit_log", "history_*", "sys_*"</p>
     */
    private List<String> readonlyTables;

    /**
     * Creates a ReadOnlyTableConfig with default settings.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>enabled: true</li>
     *   <li>riskLevel: HIGH</li>
     *   <li>readonlyTables: empty list</li>
     * </ul>
     */
    public ReadOnlyTableConfig() {
        super();
        setRiskLevel(RiskLevel.HIGH);
        this.readonlyTables = new ArrayList<>();
    }

    /**
     * Creates a ReadOnlyTableConfig with specified enabled state.
     *
     * @param enabled whether the checker should be enabled
     */
    public ReadOnlyTableConfig(boolean enabled) {
        super(enabled);
        setRiskLevel(RiskLevel.HIGH);
        this.readonlyTables = new ArrayList<>();
    }

    /**
     * Creates a ReadOnlyTableConfig with specified enabled state and readonly tables.
     *
     * @param enabled whether the checker should be enabled
     * @param readonlyTables list of readonly table names or patterns
     */
    public ReadOnlyTableConfig(boolean enabled, List<String> readonlyTables) {
        super(enabled);
        setRiskLevel(RiskLevel.HIGH);
        this.readonlyTables = readonlyTables != null ? new ArrayList<>(readonlyTables) : new ArrayList<>();
    }

    /**
     * Returns the list of read-only table names or patterns.
     *
     * @return list of readonly table names/patterns (never null)
     */
    public List<String> getReadonlyTables() {
        return readonlyTables;
    }

    /**
     * Sets the list of read-only table names or patterns.
     *
     * <p>Supports wildcard patterns ending with '*':</p>
     * <ul>
     *   <li><code>audit_log</code> - Exact match</li>
     *   <li><code>history_*</code> - Prefix match (history_users, history_orders, etc.)</li>
     * </ul>
     *
     * @param readonlyTables list of readonly table names/patterns
     */
    public void setReadonlyTables(List<String> readonlyTables) {
        this.readonlyTables = readonlyTables != null ? new ArrayList<>(readonlyTables) : new ArrayList<>();
    }
}
