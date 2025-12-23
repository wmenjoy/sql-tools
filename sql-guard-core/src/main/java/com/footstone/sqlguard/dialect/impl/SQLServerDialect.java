package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Top;

/**
 * SQL Server dialect for applying LIMIT clauses using TOP.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT TOP 1000 * FROM users;
 * </pre>
 *
 * <p>SQL Server uses TOP clause for limiting result sets.
 *
 * @since 1.1.0
 */
public class SQLServerDialect implements SqlGuardDialect {

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

    @Override
    public String getDatabaseType() {
        return "SQL Server";
    }
}

