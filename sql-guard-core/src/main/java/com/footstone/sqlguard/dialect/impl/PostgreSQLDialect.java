package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * PostgreSQL dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * <p>PostgreSQL uses the same LIMIT syntax as MySQL.
 *
 * @since 1.1.0
 */
public class PostgreSQLDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}

