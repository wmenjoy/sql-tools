package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule checker that detects and blocks DDL operations executed at application layer.
 *
 * <p>DdlOperationChecker validates SQL statements to ensure DDL (Data Definition Language)
 * operations are not executed through the application layer. This enforces the production
 * best practice that schema changes should occur via controlled migration scripts.</p>
 *
 * <h2>Why Block Application-Layer DDL?</h2>
 * <ul>
 *   <li><strong>Schema changes should be via controlled migration scripts</strong>
 *       (Flyway, Liquibase) - ensures version control, rollback capability, and audit trail</li>
 *   <li><strong>Runtime DDL indicates poor deployment practices</strong>
 *       - schema changes should be part of deployment pipeline, not runtime code</li>
 *   <li><strong>DDL operations can cause downtime and data loss</strong>
 *       - DROP TABLE, TRUNCATE TABLE are irreversible without backups</li>
 *   <li><strong>Production security</strong>
 *       - applications should not have DDL privileges in production databases</li>
 * </ul>
 *
 * <h2>Detected DDL Operations</h2>
 * <ul>
 *   <li><strong>CREATE:</strong> CREATE TABLE, CREATE INDEX, CREATE VIEW</li>
 *   <li><strong>ALTER:</strong> ALTER TABLE, ALTER INDEX</li>
 *   <li><strong>DROP:</strong> DROP TABLE, DROP INDEX, DROP VIEW</li>
 *   <li><strong>TRUNCATE:</strong> TRUNCATE TABLE</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - DDL at application layer is a severe violation</p>
 *
 * <h2>Implementation Details</h2>
 * <ul>
 *   <li>Implements RuleChecker directly (not AbstractRuleChecker) to handle DDL statement types</li>
 *   <li>Uses instanceof checks for JSqlParser DDL Statement types</li>
 *   <li>Validation logic: Check if detected DDL operation type is in config.allowedOperations</li>
 *   <li>Empty allowedOperations list = block all DDL (default secure behavior)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * // Default: Block all DDL operations
 * DdlOperationConfig config = new DdlOperationConfig();
 * DdlOperationChecker checker = new DdlOperationChecker(config);
 *
 * // Allow specific operations for migration scripts
 * config.setAllowedOperations(Arrays.asList("CREATE", "ALTER"));
 * }</pre>
 *
 * @see RuleChecker
 * @see DdlOperationConfig
 * @since 1.0.0
 */
public class DdlOperationChecker implements RuleChecker {

    private static final Logger logger = LoggerFactory.getLogger(DdlOperationChecker.class);

    private final DdlOperationConfig config;

    /**
     * Creates a DdlOperationChecker with the specified configuration.
     *
     * @param config the configuration for this checker
     */
    public DdlOperationChecker(DdlOperationConfig config) {
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
     * Checks SQL statement for DDL operations.
     *
     * <p>This method handles DDL statement types that are not covered by the standard
     * DML visitor methods (visitSelect, visitUpdate, visitDelete, visitInsert).</p>
     *
     * @param context the SQL execution context
     * @param result  the validation result accumulator
     */
    @Override
    public void check(SqlContext context, ValidationResult result) {
        // Skip if disabled
        if (!isEnabled()) {
            return;
        }

        Statement stmt = context.getStatement();
        if (stmt == null) {
            return;
        }

        // Check for DDL statement types
        String operationType = detectDdlOperationType(stmt);
        if (operationType != null) {
            // Check if this operation type is allowed
            if (!config.isOperationAllowed(operationType)) {
                addDdlViolation(result, operationType, stmt);
            }
        }
    }

    // ==================== StatementVisitor Methods (no-op) ====================
    // These methods are required by the RuleChecker interface but are not used
    // because DDL detection works on Statement types not covered by these methods.

    @Override
    public void visitSelect(Select select, SqlContext context) {
        // No-op: SELECT is not a DDL operation
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
        // No-op: UPDATE is not a DDL operation
    }

    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        // No-op: DELETE is not a DDL operation
    }

    @Override
    public void visitInsert(Insert insert, SqlContext context) {
        // No-op: INSERT is not a DDL operation
    }

    /**
     * Detects the DDL operation type from a Statement.
     *
     * @param stmt the parsed SQL statement
     * @return the DDL operation type (CREATE, ALTER, DROP, TRUNCATE), or null if not DDL
     */
    private String detectDdlOperationType(Statement stmt) {
        // CREATE operations
        if (stmt instanceof CreateTable || stmt instanceof CreateIndex || stmt instanceof CreateView) {
            return "CREATE";
        }

        // ALTER operations
        if (stmt instanceof Alter) {
            return "ALTER";
        }

        // DROP operations
        if (stmt instanceof Drop) {
            return "DROP";
        }

        // TRUNCATE operations
        if (stmt instanceof Truncate) {
            return "TRUNCATE";
        }

        return null; // Not a DDL statement
    }

    /**
     * Adds a DDL violation to the validation result.
     *
     * @param result        the validation result
     * @param operationType the DDL operation type
     * @param stmt          the DDL statement
     */
    private void addDdlViolation(ValidationResult result, String operationType, Statement stmt) {
        String message = buildViolationMessage(operationType, stmt);
        String suggestion = buildSuggestion(operationType);
        result.addViolation(RiskLevel.CRITICAL, message, suggestion);
    }

    /**
     * Builds the violation message for a DDL operation.
     *
     * @param operationType the DDL operation type
     * @param stmt          the DDL statement
     * @return the violation message
     */
    private String buildViolationMessage(String operationType, Statement stmt) {
        String stmtType = getStatementTypeName(stmt);
        return String.format("检测到 %s DDL操作: %s。应用层不应执行DDL操作,请使用数据库迁移工具(如Flyway/Liquibase)",
                operationType, stmtType);
    }

    /**
     * Gets a human-readable name for the statement type.
     *
     * @param stmt the statement
     * @return the statement type name
     */
    private String getStatementTypeName(Statement stmt) {
        if (stmt instanceof CreateTable) {
            return "CREATE TABLE";
        } else if (stmt instanceof CreateIndex) {
            return "CREATE INDEX";
        } else if (stmt instanceof CreateView) {
            return "CREATE VIEW";
        } else if (stmt instanceof Alter) {
            return "ALTER TABLE";
        } else if (stmt instanceof Drop) {
            Drop drop = (Drop) stmt;
            String type = drop.getType();
            return "DROP " + (type != null ? type.toUpperCase() : "OBJECT");
        } else if (stmt instanceof Truncate) {
            return "TRUNCATE TABLE";
        }
        return stmt.getClass().getSimpleName();
    }

    /**
     * Builds a suggestion message for fixing the DDL violation.
     *
     * @param operationType the DDL operation type
     * @return the suggestion message
     */
    private String buildSuggestion(String operationType) {
        switch (operationType) {
            case "CREATE":
                return "将CREATE语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行";
            case "ALTER":
                return "将ALTER语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行";
            case "DROP":
                return "将DROP语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行。注意:DROP操作不可逆,请谨慎操作";
            case "TRUNCATE":
                return "将TRUNCATE语句移至数据库迁移脚本或使用DELETE语句替代。注意:TRUNCATE操作不可逆,请谨慎操作";
            default:
                return "将DDL语句移至数据库迁移脚本(Flyway/Liquibase),通过部署流程执行";
        }
    }
}
