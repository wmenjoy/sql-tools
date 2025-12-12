package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.HashSet;
import java.util.Set;

/**
 * Visitor that extracts all column/field names from an SQL expression.
 * Traverses the expression tree and collects column names into a set.
 * Supports nested subqueries.
 */
public class FieldExtractorVisitor extends ExpressionVisitorAdapter {

    private final Set<String> fieldNames = new HashSet<>();

    /**
     * Returns the set of field names collected during traversal.
     *
     * @return set of field names (column names without table prefixes)
     */
    public Set<String> getFieldNames() {
        return fieldNames;
    }

    @Override
    public void visit(Column column) {
        // Extract column name without table prefix
        String columnName = column.getColumnName();
        if (columnName != null) {
            fieldNames.add(columnName);
        }
    }

    @Override
    public void visit(SubSelect subSelect) {
        // Handle subqueries by extracting fields from their WHERE clauses
        try {
            if (subSelect.getSelectBody() instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) subSelect.getSelectBody();
                if (plainSelect.getWhere() != null) {
                    plainSelect.getWhere().accept(this);
                }
            }
        } catch (Exception e) {
            // Ignore errors in subquery processing
        }
    }
}
