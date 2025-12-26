package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * Oracle dialect for applying LIMIT clauses using ROWNUM.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM (
 *     SELECT * FROM users
 * ) WHERE ROWNUM &lt;= 1000;
 * </pre>
 *
 * <p>Oracle uses ROWNUM pseudo-column for pagination, requiring subquery wrapping.
 *
 * <h2>Implementation Notes</h2>
 * <p>This implementation wraps the original SELECT in a subquery and adds
 * a WHERE ROWNUM &lt;= limit condition to the outer query. This is the
 * traditional Oracle pagination approach (pre-12c).
 *
 * @since 1.1.0
 */
public class OracleDialect implements SqlGuardDialect {

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

    @Override
    public String getDatabaseType() {
        return "Oracle";
    }
}







