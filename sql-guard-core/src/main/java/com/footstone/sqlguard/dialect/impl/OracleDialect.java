package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * Oracle dialect for applying LIMIT clauses.
 *
 * <h2>Supported Syntaxes</h2>
 * <ul>
 *   <li><strong>Legacy (pre-12c):</strong> ROWNUM pseudo-column with subquery wrapping</li>
 *   <li><strong>Modern (12c+):</strong> FETCH FIRST n ROWS ONLY</li>
 * </ul>
 *
 * <h2>Legacy Syntax (ROWNUM)</h2>
 * <pre>
 * SELECT * FROM (
 *     SELECT * FROM users
 * ) WHERE ROWNUM &lt;= 1000;
 * </pre>
 *
 * <h2>Modern Syntax (FETCH FIRST)</h2>
 * <pre>
 * SELECT * FROM users FETCH FIRST 1000 ROWS ONLY;
 * </pre>
 *
 * <p>The default {@link #applyLimit(Select, long)} uses ROWNUM for backward compatibility.
 * Use {@link #applyLimitModern(Select, long)} for Oracle 12c+ with cleaner syntax.</p>
 *
 * @since 1.1.0 (FETCH FIRST support added in 1.2.0)
 */
public class OracleDialect implements SqlGuardDialect {

    /**
     * Applies LIMIT clause using legacy ROWNUM syntax (pre-12c).
     *
     * <p>This method wraps the original SELECT in a subquery and adds
     * a WHERE ROWNUM &lt;= limit condition to the outer query.</p>
     *
     * @param select SELECT statement to modify
     * @param limit maximum number of rows to return
     */
    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect originalSelect = (PlainSelect) selectBody;

            // Wrap original select in subquery
            SubSelect subSelect = new SubSelect();
            subSelect.setSelectBody(originalSelect);

            // Create outer select with ROWNUM condition
            PlainSelect outerSelect = new PlainSelect();
            outerSelect.setFromItem(subSelect);
            outerSelect.addSelectItems(new AllColumns());

            // Add WHERE ROWNUM <= limit
            MinorThanEquals rownumCondition = new MinorThanEquals();
            rownumCondition.setLeftExpression(new Column("ROWNUM"));
            rownumCondition.setRightExpression(new LongValue(limit));
            outerSelect.setWhere(rownumCondition);

            // Replace original select body
            select.setSelectBody(outerSelect);
        }
    }

    /**
     * Applies LIMIT clause using modern FETCH FIRST syntax (Oracle 12c+).
     *
     * <p>This method uses the SQL:2008 standard FETCH FIRST clause, which is
     * cleaner and more efficient than the legacy ROWNUM approach.</p>
     *
     * <h2>Generated Syntax</h2>
     * <pre>
     * SELECT * FROM users FETCH FIRST 1000 ROWS ONLY;
     * </pre>
     *
     * @param select SELECT statement to modify
     * @param limit maximum number of rows to return
     * @since 1.2.0
     */
    public void applyLimitModern(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Create FETCH FIRST n ROWS ONLY clause
            Fetch fetch = new Fetch();
            fetch.setRowCount(limit);
            plainSelect.setFetch(fetch);
        }
    }

    @Override
    public String getDatabaseType() {
        return "Oracle";
    }
}








