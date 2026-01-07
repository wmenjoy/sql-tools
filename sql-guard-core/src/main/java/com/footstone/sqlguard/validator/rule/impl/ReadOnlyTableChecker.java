package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

import java.util.List;

/**
 * Rule checker that protects read-only tables from write operations.
 *
 * <p>ReadOnlyTableChecker validates INSERT, UPDATE, and DELETE statements to ensure they
 * do not target read-only tables. This is useful for protecting:</p>
 * <ul>
 *   <li><strong>Audit Logs:</strong> Must be immutable for compliance and forensics</li>
 *   <li><strong>Historical Records:</strong> Cannot be modified to preserve data integrity</li>
 *   <li><strong>Reference Data:</strong> Read-only lookup tables that should not change</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> HIGH</p>
 * <p>Write operations on read-only tables can cause data integrity violations but are
 * not as severe as structure changes (CRITICAL). Application-level protection complements
 * database-level permissions.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Extract target table name from INSERT/UPDATE/DELETE statement</li>
 *   <li>Check if target table matches any pattern in readonlyTables config</li>
 *   <li>If matched, add HIGH violation</li>
 *   <li><strong>IMPORTANT:</strong> Only the target table is checked, NOT tables in WHERE clause or subqueries</li>
 * </ol>
 *
 * <p><strong>Wildcard Pattern Support:</strong></p>
 * <p>Supports wildcard patterns using '*' at the end:</p>
 * <ul>
 *   <li><code>audit_log</code> - Exact match</li>
 *   <li><code>history_*</code> - Matches history_users, history_orders, etc.</li>
 * </ul>
 *
 * <p><strong>Examples:</strong></p>
 * <pre>{@code
 * // VIOLATES - writing to readonly table
 * INSERT INTO audit_log (user_id, action) VALUES (1, 'login')
 * UPDATE audit_log SET action = 'modified' WHERE id = 1
 * DELETE FROM history_users WHERE id = 1
 *
 * // PASSES - reading readonly table is allowed
 * SELECT * FROM audit_log WHERE user_id = 1
 *
 * // PASSES - readonly table only in WHERE clause (not target)
 * UPDATE users SET status = 1 WHERE id IN (SELECT user_id FROM audit_log)
 * }</pre>
 *
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li>Uses visitor pattern: overrides visitInsert(), visitUpdate(), visitDelete()</li>
 *   <li>Direct API usage: insert.getTable(), update.getTable(), delete.getTable()</li>
 *   <li>Case-insensitive matching for table names and patterns</li>
 *   <li>Handles schema-qualified names (extracts table name only)</li>
 * </ul>
 *
 * @see ReadOnlyTableConfig
 * @see AbstractRuleChecker
 * @since 1.0.0
 */
public class ReadOnlyTableChecker extends AbstractRuleChecker {

    private final ReadOnlyTableConfig config;

    /**
     * Creates a ReadOnlyTableChecker with the specified configuration.
     *
     * @param config the configuration defining readonly tables
     */
    public ReadOnlyTableChecker(ReadOnlyTableConfig config) {
        super(config);
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
     * Validates INSERT statements for write operations on readonly tables.
     *
     * <p>Extracts the target table from INSERT statement and checks if it matches
     * any readonly table pattern.</p>
     *
     * @param insert the INSERT statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.0.0
     */
    @Override
    public void visitInsert(Insert insert, SqlContext context) {
        if (!isEnabled()) {
            return;
        }

        Table table = insert.getTable();
        if (table == null) {
            return;
        }

        String tableName = extractTableName(table);
        if (isReadonlyTable(tableName)) {
            addViolation(
                    RiskLevel.HIGH,
                    String.format("Write operation INSERT on read-only table: %s", tableName),
                    "Read-only tables cannot be modified. Remove INSERT operation or use a different table."
            );
        }
    }

    /**
     * Validates UPDATE statements for write operations on readonly tables.
     *
     * <p>Extracts the target table from UPDATE statement and checks if it matches
     * any readonly table pattern. Tables in WHERE clause are NOT checked.</p>
     *
     * @param update the UPDATE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.0.0
     */
    @Override
    public void visitUpdate(Update update, SqlContext context) {
        if (!isEnabled()) {
            return;
        }

        Table table = update.getTable();
        if (table == null) {
            return;
        }

        String tableName = extractTableName(table);
        if (isReadonlyTable(tableName)) {
            addViolation(
                    RiskLevel.HIGH,
                    String.format("Write operation UPDATE on read-only table: %s", tableName),
                    "Read-only tables cannot be modified. Remove UPDATE operation or use a different table."
            );
        }
    }

    /**
     * Validates DELETE statements for write operations on readonly tables.
     *
     * <p>Extracts the target table from DELETE statement and checks if it matches
     * any readonly table pattern. Tables in WHERE clause are NOT checked.</p>
     *
     * @param delete the DELETE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.0.0
     */
    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        if (!isEnabled()) {
            return;
        }

        Table table = delete.getTable();
        if (table == null) {
            return;
        }

        String tableName = extractTableName(table);
        if (isReadonlyTable(tableName)) {
            addViolation(
                    RiskLevel.HIGH,
                    String.format("Write operation DELETE on read-only table: %s", tableName),
                    "Read-only tables cannot be modified. Remove DELETE operation or use a different table."
            );
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts the table name from a Table object.
     *
     * <p>Handles schema-qualified names by extracting only the table name portion.
     * Also strips quotes (backticks, double quotes) from the name.</p>
     *
     * @param table the Table object
     * @return the table name (without schema prefix or quotes)
     */
    private String extractTableName(Table table) {
        String name = table.getName();
        if (name == null) {
            return null;
        }

        // Strip quotes (MySQL backticks, PostgreSQL/Oracle double quotes)
        name = name.replace("`", "").replace("\"", "");

        return name;
    }

    /**
     * Checks if a table name matches any readonly table pattern.
     *
     * <p>Matching rules:</p>
     * <ul>
     *   <li>Case-insensitive comparison</li>
     *   <li>Exact match: "audit_log" matches "audit_log"</li>
     *   <li>Wildcard match: "history_*" matches "history_users", "history_orders", etc.</li>
     * </ul>
     *
     * @param tableName the table name to check
     * @return true if the table is readonly, false otherwise
     */
    private boolean isReadonlyTable(String tableName) {
        if (tableName == null) {
            return false;
        }

        List<String> readonlyTables = config.getReadonlyTables();
        if (readonlyTables == null || readonlyTables.isEmpty()) {
            return false;
        }

        String tableNameLower = tableName.toLowerCase();

        for (String pattern : readonlyTables) {
            if (pattern == null) {
                continue;
            }

            String patternLower = pattern.toLowerCase();

            // Exact match
            if (tableNameLower.equals(patternLower)) {
                return true;
            }

            // Wildcard pattern (e.g., "history_*")
            if (patternLower.endsWith("*")) {
                String prefix = patternLower.substring(0, patternLower.length() - 1);
                if (tableNameLower.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }
}
