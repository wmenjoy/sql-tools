package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * 达梦数据库 (DM Database) dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * <p>达梦数据库支持标准的 LIMIT 语法，类似于 MySQL。
 *
 * <h2>兼容性</h2>
 * <p>达梦数据库 (武汉达梦数据库股份有限公司) 是中国自主研发的关系型数据库，
 * 兼容 Oracle 和 MySQL 语法。
 *
 * @since 1.1.0
 */
public class DmDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 达梦支持 LIMIT 语法
            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "DM";
    }
}

