package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit checker that detects destructive operations (UPDATE/DELETE) 
 * that modified data without a WHERE clause (unbounded mutation).
 */
public class ActualImpactNoWhereChecker extends AbstractAuditChecker {

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        // 1. Pre-check: Must have affected rows to be "Actual Impact"
        if (result.getRowsAffected() <= 0) {
            return null;
        }

        try {
            // 2. Parse SQL
            Statement stmt = CCJSqlParserUtil.parse(sql);
            
            boolean isUnboundedMutation = false;
            String statementType = "";

            if (stmt instanceof Update) {
                Update update = (Update) stmt;
                if (update.getWhere() == null) {
                    isUnboundedMutation = true;
                    statementType = "UPDATE";
                }
            } else if (stmt instanceof Delete) {
                Delete delete = (Delete) stmt;
                if (delete.getWhere() == null) {
                    isUnboundedMutation = true;
                    statementType = "DELETE";
                }
            }

            if (isUnboundedMutation) {
                Map<String, Number> metrics = new HashMap<>();
                metrics.put("rows_affected", result.getRowsAffected());

                return RiskScore.builder()
                        .severity(RiskLevel.CRITICAL)
                        .confidence(100)
                        .justification(String.format("Unbounded data mutation detected: %s without WHERE clause modified %d rows.", 
                                statementType, result.getRowsAffected()))
                        .impactMetrics(metrics)
                        .build();
            }

        } catch (JSQLParserException e) {
            // Fallback or ignore.
            // Requirement: "Safety Net: If parsing fails... return MEDIUM risk 'Analysis Failed' (don't crash)"
            // However, we can also try regex if parsing fails.
            // For now, let's just return a MEDIUM risk indicating we couldn't verify safety.
            return RiskScore.builder()
                    .severity(RiskLevel.MEDIUM)
                    .confidence(50)
                    .justification("SQL Analysis Failed: Could not parse statement to verify safety constraints.")
                    .build();
        }

        return null;
    }

    @Override
    public String getCheckerId() {
        return "ActualImpactNoWhereChecker";
    }
}
