package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit checker that detects SELECT queries returning excessive rows (Unbounded Read).
 */
public class UnboundedReadChecker extends AbstractAuditChecker {

    private final int maxRowLimit;
    private static final int DEFAULT_MAX_ROW_LIMIT = 10000;

    public UnboundedReadChecker() {
        this(DEFAULT_MAX_ROW_LIMIT);
    }

    public UnboundedReadChecker(int maxRowLimit) {
        this.maxRowLimit = maxRowLimit;
    }

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        // Use resultSetSize if available, otherwise fallback to rowsAffected if safe assumption
        // Usually resultSetSize is for SELECT.
        int rowCount = result.getResultSetSize() != null ? result.getResultSetSize() : 0;

        if (rowCount <= maxRowLimit) {
            return null;
        }

        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select) {
                Map<String, Number> metrics = new HashMap<>();
                metrics.put("result_set_size", rowCount);
                metrics.put("limit_exceeded_by", rowCount - maxRowLimit);
                metrics.put("limit", maxRowLimit);

                return RiskScore.builder()
                        .severity(RiskLevel.HIGH)
                        .confidence(100)
                        .justification(String.format("Large result set fetching %d rows may cause OOM or network saturation (Limit: %d).", 
                                rowCount, maxRowLimit))
                        .impactMetrics(metrics)
                        .build();
            }
        } catch (JSQLParserException e) {
            // Ignore parse errors, maybe return nothing or specific risk.
            // If row count is huge but we can't parse, we might still want to flag it if we are sure it's a query?
            // But without parsing we don't know if it is SELECT.
            // However, resultSetSize usually implies SELECT.
            
            // For safety, let's assume if resultSetSize > 0 it is likely a read operation.
            // But let's stick to strict parsing to avoid false positives on weird statements.
        }

        return null;
    }

    @Override
    public String getCheckerId() {
        return "UnboundedReadChecker";
    }
}
