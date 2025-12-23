package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.First;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * IBM Informix dialect for applying LIMIT clauses using FIRST.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT FIRST 1000 * FROM users;
 * </pre>
 *
 * <p>Informix uses FIRST n for limiting result sets.
 *
 * @since 1.1.0
 */
public class InformixDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // Create FIRST n clause with keyword
            First first = new First();
            first.setKeyword(First.Keyword.FIRST);
            first.setRowCount(limit);

            plainSelect.setFirst(first);
        }
    }

    @Override
    public String getDatabaseType() {
        return "Informix";
    }
}

