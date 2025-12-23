package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * IBM DB2 dialect for applying LIMIT clauses using FETCH FIRST.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users FETCH FIRST 1000 ROWS ONLY;
 * </pre>
 *
 * <p>DB2 uses FETCH FIRST n ROWS ONLY for limiting result sets.
 *
 * @since 1.1.0
 */
public class DB2Dialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Create FETCH FIRST n ROWS ONLY clause
            Fetch fetch = new Fetch();
            fetch.setRowCount(limit);
            // Note: JSqlParser 4.6 doesn't have setFetchOnly method
            // The FETCH clause will generate "FETCH FIRST n ROWS ONLY" by default

            plainSelect.setFetch(fetch);
        }
    }

    @Override
    public String getDatabaseType() {
        return "DB2";
    }
}

