package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Top;

/**
 * SQL Server dialect for applying LIMIT clauses.
 *
 * <h2>Supported Syntaxes</h2>
 * <ul>
 *   <li><strong>Legacy (all versions):</strong> TOP n</li>
 *   <li><strong>Modern (2012+):</strong> OFFSET m ROWS FETCH NEXT n ROWS ONLY</li>
 * </ul>
 *
 * <h2>TOP Syntax</h2>
 * <pre>
 * SELECT TOP 1000 * FROM users;
 * </pre>
 *
 * <h2>OFFSET/FETCH Syntax (SQL Server 2012+)</h2>
 * <pre>
 * SELECT * FROM users ORDER BY id OFFSET 0 ROWS FETCH NEXT 1000 ROWS ONLY;
 * </pre>
 *
 * <p>The default {@link #applyLimit(Select, long)} uses TOP for backward compatibility.
 * Use {@link #applyLimitWithOffset(Select, long, long)} for SQL Server 2012+ with
 * pagination support (requires ORDER BY clause).</p>
 *
 * @since 1.1.0 (OFFSET/FETCH support added in 1.2.0)
 */
public class SQLServerDialect implements SqlGuardDialect {

    /**
     * Applies LIMIT clause using legacy TOP syntax.
     *
     * <p>Works with all SQL Server versions. Does not support offset.</p>
     *
     * @param select SELECT statement to modify
     * @param limit maximum number of rows to return
     */
    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Create TOP clause
            Top top = new Top();
            top.setExpression(new LongValue(limit));

            plainSelect.setTop(top);
        }
    }

    /**
     * Applies pagination using modern OFFSET/FETCH syntax (SQL Server 2012+).
     *
     * <p>This method uses the SQL:2008 standard OFFSET/FETCH clause, which supports
     * both offset and limit. <strong>Important:</strong> The original query must have
     * an ORDER BY clause for this syntax to work.</p>
     *
     * <h2>Generated Syntax</h2>
     * <pre>
     * SELECT * FROM users ORDER BY id OFFSET 20 ROWS FETCH NEXT 100 ROWS ONLY;
     * </pre>
     *
     * @param select SELECT statement to modify (must have ORDER BY)
     * @param offset number of rows to skip (0 for no skip)
     * @param limit maximum number of rows to return
     * @since 1.2.0
     */
    public void applyLimitWithOffset(Select select, long offset, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Create OFFSET clause: OFFSET n ROWS
            Offset offsetClause = new Offset();
            offsetClause.setOffset(new LongValue(offset));
            offsetClause.setOffsetParam("ROWS");
            plainSelect.setOffset(offsetClause);

            // Create FETCH NEXT clause: FETCH NEXT n ROWS ONLY
            Fetch fetch = new Fetch();
            fetch.setRowCount(limit);
            plainSelect.setFetch(fetch);
        }
    }

    @Override
    public String getDatabaseType() {
        return "SQL Server";
    }
}

