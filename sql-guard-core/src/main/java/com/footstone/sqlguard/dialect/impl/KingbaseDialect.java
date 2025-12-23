package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;

/**
 * 金仓数据库 (KingbaseES) dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * <p>金仓数据库支持 PostgreSQL 兼容的 LIMIT 语法。
 *
 * <h2>兼容性</h2>
 * <p>金仓数据库 (北京人大金仓信息技术股份有限公司) 是基于 PostgreSQL 开发的
 * 国产数据库，兼容 PostgreSQL 语法。
 *
 * @since 1.1.0
 */
public class KingbaseDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            // 金仓兼容 PostgreSQL，支持 LIMIT 语法
            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "KingbaseES";
    }
}

