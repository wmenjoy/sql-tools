package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rule checker that enforces table-level access control blacklist.
 *
 * <p>DeniedTableChecker prevents access to sensitive tables by detecting denied table names
 * in SQL statements. It extracts all table references from FROM, JOIN, subqueries, and CTEs,
 * then matches them against a configurable denied list with wildcard pattern support.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Comprehensive table extraction using JSqlParser's TablesNamesFinder</li>
 *   <li>Wildcard pattern support with correct semantics (sys_* matches sys_user but NOT system)</li>
 *   <li>Case-insensitive matching</li>
 *   <li>Detects tables in all SQL locations (FROM, JOIN, subqueries, CTEs)</li>
 *   <li>Supports all statement types (SELECT, INSERT, UPDATE, DELETE)</li>
 * </ul>
 *
 * <h2>Table Extraction Coverage</h2>
 * <ul>
 *   <li>FROM clause tables</li>
 *   <li>JOIN clause tables (INNER, LEFT, RIGHT, FULL, CROSS)</li>
 *   <li>Subqueries in SELECT, WHERE, FROM clauses</li>
 *   <li>CTEs (WITH clause)</li>
 *   <li>INSERT INTO target tables</li>
 *   <li>UPDATE target tables</li>
 *   <li>DELETE FROM target tables</li>
 * </ul>
 *
 * <h2>Wildcard Pattern Semantics</h2>
 * <p>The asterisk (*) wildcard matches one or more characters that do NOT include
 * additional underscores, providing word-boundary-like matching:</p>
 * <ul>
 *   <li>{@code sys_*} matches: sys_user, sys_config, sys_role</li>
 *   <li>{@code sys_*} does NOT match: system (missing underscore), sys_user_detail (extra underscore)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DeniedTableConfig config = new DeniedTableConfig();
 * config.setEnabled(true);
 * config.setDeniedTables(Arrays.asList("sys_*", "admin_*", "audit_log"));
 * 
 * DeniedTableChecker checker = new DeniedTableChecker(config);
 * 
 * // This will fail: sys_user matches sys_* pattern
 * SqlContext context = createContext("SELECT * FROM sys_user WHERE id = 1");
 * ValidationResult result = new ValidationResult();
 * checker.check(context, result);
 * // result.isPassed() == false
 * }</pre>
 *
 * <h2>Security Note</h2>
 * <p>This checker provides a defense-in-depth layer for table access control.
 * Database-level permissions remain the primary access control mechanism.
 * This checker adds application-level protection against accidental or malicious
 * access to sensitive tables.</p>
 *
 * @see DeniedTableConfig
 * @see AbstractRuleChecker
 * @see TablesNamesFinder
 * @since 1.0.0
 */
public class DeniedTableChecker extends AbstractRuleChecker {

    private final DeniedTableConfig config;

    /**
     * Compiled regex patterns for denied tables.
     * Lazily initialized on first use for performance.
     */
    private List<Pattern> compiledPatterns;

    /**
     * Creates a DeniedTableChecker with the specified configuration.
     *
     * @param config the denied table configuration
     */
    public DeniedTableChecker(DeniedTableConfig config) {
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
        return config != null && config.isEnabled();
    }

    // ==================== Visitor Methods ====================

    /**
     * Validates SELECT statements for denied table access.
     *
     * @param select  the SELECT statement
     * @param context the SQL execution context
     */
    @Override
    public void visitSelect(Select select, SqlContext context) {
        if (!isEnabled() || config.isDeniedTablesEmpty()) {
            return;
        }
        checkDeniedTables(select, context);
    }

    /**
     * Validates UPDATE statements for denied table access.
     *
     * @param update  the UPDATE statement
     * @param context the SQL execution context
     */
    @Override
    public void visitUpdate(Update update, SqlContext context) {
        if (!isEnabled() || config.isDeniedTablesEmpty()) {
            return;
        }
        checkDeniedTables(update, context);
    }

    /**
     * Validates DELETE statements for denied table access.
     *
     * @param delete  the DELETE statement
     * @param context the SQL execution context
     */
    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        if (!isEnabled() || config.isDeniedTablesEmpty()) {
            return;
        }
        checkDeniedTables(delete, context);
    }

    /**
     * Validates INSERT statements for denied table access.
     *
     * @param insert  the INSERT statement
     * @param context the SQL execution context
     */
    @Override
    public void visitInsert(Insert insert, SqlContext context) {
        if (!isEnabled() || config.isDeniedTablesEmpty()) {
            return;
        }
        checkDeniedTables(insert, context);
    }

    // ==================== Core Logic ====================

    /**
     * Checks if any tables in the statement are in the denied list.
     *
     * <p>Uses JSqlParser's TablesNamesFinder to extract all table names from
     * the statement, including tables in FROM, JOIN, subqueries, and CTEs.</p>
     *
     * @param statement the SQL statement to check
     * @param context   the SQL execution context
     */
    private void checkDeniedTables(Statement statement, SqlContext context) {
        // Extract all table names using TablesNamesFinder
        List<String> tableNames = extractAllTableNames(statement);
        if (tableNames.isEmpty()) {
            return;
        }

        // Ensure patterns are compiled
        ensurePatternsCompiled();

        // Check each table against denied patterns
        List<String> deniedTablesFound = new ArrayList<>();
        for (String fullTableName : tableNames) {
            // Extract pure table name (without schema prefix)
            String tableName = extractTableNameOnly(fullTableName);
            if (tableName == null || tableName.isEmpty()) {
                continue;
            }

            // Check against each denied pattern
            if (isTableDenied(tableName)) {
                deniedTablesFound.add(tableName);
            }
        }

        // Report violations
        if (!deniedTablesFound.isEmpty()) {
            String message = buildViolationMessage(deniedTablesFound);
            String suggestion = "Remove access to denied tables or request access permission";
            addViolation(RiskLevel.CRITICAL, message, suggestion);
        }
    }

    /**
     * Extracts all table names from a SQL statement using TablesNamesFinder.
     *
     * <p>TablesNamesFinder automatically handles:</p>
     * <ul>
     *   <li>FROM clause tables</li>
     *   <li>JOIN clause tables</li>
     *   <li>Subqueries in SELECT/WHERE/FROM</li>
     *   <li>CTEs (WITH clause)</li>
     * </ul>
     *
     * @param statement the SQL statement
     * @return list of table names (may include schema prefix like "schema.table")
     */
    private List<String> extractAllTableNames(Statement statement) {
        try {
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            return tablesNamesFinder.getTableList(statement);
        } catch (Exception e) {
            logger.warn("Failed to extract table names from statement: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Extracts the pure table name from a potentially schema-qualified name.
     *
     * <p>Handles formats like:</p>
     * <ul>
     *   <li>"table_name" → "table_name"</li>
     *   <li>"schema.table_name" → "table_name"</li>
     *   <li>"catalog.schema.table_name" → "table_name"</li>
     *   <li>"`table_name`" → "table_name" (MySQL backticks)</li>
     *   <li>"\"TABLE_NAME\"" → "TABLE_NAME" (Oracle/PostgreSQL double quotes)</li>
     *   <li>"[table_name]" → "table_name" (SQL Server brackets)</li>
     * </ul>
     *
     * @param fullTableName the full table name (may include schema/catalog and delimiters)
     * @return the pure table name without schema prefix and delimiters
     */
    private String extractTableNameOnly(String fullTableName) {
        if (fullTableName == null || fullTableName.isEmpty()) {
            return null;
        }
        
        // Extract table name after last dot (remove schema/catalog prefix)
        int dotIndex = fullTableName.lastIndexOf('.');
        String tableName = dotIndex >= 0 ? fullTableName.substring(dotIndex + 1) : fullTableName;
        
        // Remove database-specific delimiters
        tableName = removeDelimiters(tableName);
        
        return tableName;
    }

    /**
     * Removes database-specific delimiters from table identifiers.
     *
     * <p>Handles:</p>
     * <ul>
     *   <li>MySQL backticks: `table_name` → table_name</li>
     *   <li>Oracle/PostgreSQL double quotes: "TABLE_NAME" → TABLE_NAME</li>
     *   <li>SQL Server brackets: [table_name] → table_name</li>
     * </ul>
     *
     * @param identifier the identifier to clean
     * @return identifier without delimiters
     */
    private String removeDelimiters(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        
        // Remove backticks (MySQL)
        if (identifier.startsWith("`") && identifier.endsWith("`") && identifier.length() > 2) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        // Remove double quotes (Oracle/PostgreSQL)
        if (identifier.startsWith("\"") && identifier.endsWith("\"") && identifier.length() > 2) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        // Remove square brackets (SQL Server)
        if (identifier.startsWith("[") && identifier.endsWith("]") && identifier.length() > 2) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        return identifier;
    }

    /**
     * Checks if a table name matches any denied pattern.
     *
     * @param tableName the table name to check (without schema prefix)
     * @return true if the table is denied, false otherwise
     */
    private boolean isTableDenied(String tableName) {
        if (tableName == null || compiledPatterns == null) {
            return false;
        }

        String tableNameLower = tableName.toLowerCase();
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(tableNameLower).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures that denied table patterns are compiled to regex patterns.
     * <p>This method is thread-safe and only compiles patterns once.</p>
     */
    private synchronized void ensurePatternsCompiled() {
        if (compiledPatterns != null) {
            return;
        }

        compiledPatterns = new ArrayList<>();
        List<String> deniedTables = config.getDeniedTables();
        if (deniedTables == null) {
            return;
        }

        for (String pattern : deniedTables) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                Pattern compiled = convertWildcardToRegex(pattern.trim().toLowerCase());
                if (compiled != null) {
                    compiledPatterns.add(compiled);
                }
            }
        }
    }

    /**
     * Converts a wildcard pattern to a Java regex Pattern.
     *
     * <h3>Wildcard Semantics</h3>
     * <p>The asterisk (*) matches one or more characters that do NOT include underscores.
     * This provides word-boundary-like matching:</p>
     * <ul>
     *   <li>{@code sys_*} → {@code ^sys_[^_]+$}</li>
     *   <li>Matches: sys_user, sys_config</li>
     *   <li>Does NOT match: system (no underscore), sys_user_detail (extra underscore)</li>
     * </ul>
     *
     * <h3>Pattern Conversion Rules</h3>
     * <ul>
     *   <li>Asterisk (*) → [^_]+ (one or more non-underscore characters)</li>
     *   <li>All other characters are escaped for literal matching</li>
     *   <li>Patterns without wildcards match exactly</li>
     * </ul>
     *
     * @param wildcardPattern the wildcard pattern (e.g., "sys_*")
     * @return compiled regex Pattern for case-insensitive matching
     */
    private Pattern convertWildcardToRegex(String wildcardPattern) {
        if (wildcardPattern == null || wildcardPattern.isEmpty()) {
            return null;
        }

        StringBuilder regex = new StringBuilder("^");
        
        // Process each character
        for (int i = 0; i < wildcardPattern.length(); i++) {
            char c = wildcardPattern.charAt(i);
            if (c == '*') {
                // Asterisk matches one or more non-underscore characters
                regex.append("[^_]+");
            } else if (isRegexSpecialChar(c)) {
                // Escape regex special characters
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
        }
        
        regex.append("$");

        try {
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            logger.warn("Failed to compile pattern '{}': {}", wildcardPattern, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a character is a regex special character that needs escaping.
     *
     * @param c the character to check
     * @return true if the character needs escaping
     */
    private boolean isRegexSpecialChar(char c) {
        return "\\[]{}()^$.|+?".indexOf(c) >= 0;
    }

    /**
     * Builds a violation message for denied tables.
     *
     * @param deniedTables list of denied table names found
     * @return formatted violation message
     */
    private String buildViolationMessage(List<String> deniedTables) {
        if (deniedTables.size() == 1) {
            return String.format("Access to denied table '%s' is not allowed", deniedTables.get(0));
        } else {
            return String.format("Access to denied tables %s is not allowed", deniedTables);
        }
    }
}
