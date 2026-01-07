package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for DeniedTableChecker.
 *
 * <p>Defines table-level access control blacklist with wildcard pattern support.
 * Tables in the denied list will be blocked from access in SQL statements.</p>
 *
 * <h2>Wildcard Pattern Support</h2>
 * <p>The deniedTables list supports wildcard patterns using asterisk (*).
 * <strong>IMPORTANT:</strong> The asterisk matches one or more characters that do NOT include
 * additional underscores, providing word-boundary-like matching.</p>
 *
 * <h3>Pattern Examples</h3>
 * <ul>
 *   <li>{@code sys_*} - Matches {@code sys_user}, {@code sys_config}
 *       but <strong>NOT</strong> {@code system} (missing underscore) or {@code sys_user_detail} (extra underscore)</li>
 *   <li>{@code admin_*} - Matches {@code admin_users}, {@code admin_config}
 *       but <strong>NOT</strong> {@code administrator}</li>
 *   <li>{@code audit_log} - Exact match only, matches {@code audit_log} but not {@code audit_log_backup}</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * DeniedTableConfig config = new DeniedTableConfig();
 * config.setEnabled(true);
 * 
 * // Add denied tables (exact names and patterns)
 * List<String> deniedTables = new ArrayList<>();
 * deniedTables.add("sys_*");           // Block all sys_ prefixed tables
 * deniedTables.add("admin_*");         // Block all admin_ prefixed tables
 * deniedTables.add("audit_log");       // Block specific table
 * deniedTables.add("sensitive_data");  // Block specific table
 * config.setDeniedTables(deniedTables);
 * }</pre>
 *
 * <h2>YAML Configuration</h2>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     denied-table:
 *       enabled: true
 *       denied-tables:
 *         - "sys_*"
 *         - "admin_*"
 *         - "audit_log"
 *         - "sensitive_data"
 * }</pre>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Protecting sensitive system tables (sys_user, sys_config)</li>
 *   <li>Preventing access to admin tables</li>
 *   <li>Blocking audit/log tables from application queries</li>
 *   <li>Defense-in-depth layer (complements database-level permissions)</li>
 * </ul>
 *
 * @see DeniedTableChecker
 * @see CheckerConfig
 * @since 1.0.0
 */
public class DeniedTableConfig extends CheckerConfig {

    /**
     * List of denied table names or patterns.
     * <p>Supports exact table names and wildcard patterns using asterisk (*).
     * Patterns are matched case-insensitively.</p>
     * 
     * <p>Default: empty list (no tables denied)</p>
     */
    private List<String> deniedTables;

    /**
     * Creates a DeniedTableConfig with default settings.
     * <p>Default: enabled=true, deniedTables=empty list</p>
     */
    public DeniedTableConfig() {
        super();
        this.deniedTables = new ArrayList<>();
    }

    /**
     * Creates a DeniedTableConfig with specified enabled state.
     *
     * @param enabled whether the checker should be enabled
     */
    public DeniedTableConfig(boolean enabled) {
        super(enabled);
        this.deniedTables = new ArrayList<>();
    }

    /**
     * Returns the list of denied table names or patterns.
     *
     * @return list of denied tables (may contain wildcards)
     */
    public List<String> getDeniedTables() {
        return deniedTables;
    }

    /**
     * Sets the list of denied table names or patterns.
     * <p>Patterns are stored as-is and matched case-insensitively at runtime.</p>
     *
     * @param deniedTables list of denied tables (may contain wildcards)
     */
    public void setDeniedTables(List<String> deniedTables) {
        this.deniedTables = deniedTables != null ? deniedTables : new ArrayList<>();
    }

    /**
     * Adds a single table name or pattern to the denied list.
     *
     * @param tablePattern table name or pattern to deny
     */
    public void addDeniedTable(String tablePattern) {
        if (tablePattern != null && !tablePattern.trim().isEmpty()) {
            if (this.deniedTables == null) {
                this.deniedTables = new ArrayList<>();
            }
            this.deniedTables.add(tablePattern.trim());
        }
    }

    /**
     * Checks if the denied tables list is empty.
     *
     * @return true if no tables are denied, false otherwise
     */
    public boolean isDeniedTablesEmpty() {
        return deniedTables == null || deniedTables.isEmpty();
    }
}
