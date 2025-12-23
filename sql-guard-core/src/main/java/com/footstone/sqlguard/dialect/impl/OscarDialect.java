package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * 神通数据库 (Oscar/OSCAR) dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * <p>神通数据库支持标准的 LIMIT 语法。
 *
 * <h2>兼容性</h2>
 * <p>神通数据库 (天津神舟通用数据技术有限公司) 是中国自主研发的关系型数据库，
 * 支持标准 SQL 语法。
 *
 * @since 1.1.0
 */
public class OscarDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 神通支持 LIMIT 语法
            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "Oscar";
    }
}

