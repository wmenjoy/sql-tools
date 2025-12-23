package com.footstone.sqlguard.dialect;

import net.sf.jsqlparser.statement.select.Select;

/**
 * Database dialect interface for applying LIMIT clauses in database-specific syntax.
 *
 * <h2>Purpose</h2>
 * <p>Abstracts database-specific pagination syntax differences, allowing
 * {@code SelectLimitInnerInterceptor} to support multiple databases without conditional logic.
 *
 * <h2>Supported Databases</h2>
 * <ul>
 *   <li>MySQL: {@code LIMIT n}</li>
 *   <li>PostgreSQL: {@code LIMIT n}</li>
 *   <li>Oracle: {@code WHERE ROWNUM <= n}</li>
 *   <li>SQL Server: {@code TOP n}</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);
 * dialect.applyLimit(select, 1000);
 * }</pre>
 *
 * @see com.footstone.sqlguard.dialect.impl.MySQLDialect
 * @see com.footstone.sqlguard.dialect.impl.OracleDialect
 * @see com.footstone.sqlguard.dialect.impl.SQLServerDialect
 * @see com.footstone.sqlguard.dialect.impl.PostgreSQLDialect
 * @since 1.1.0
 */
public interface SqlGuardDialect {

    /**
     * Applies LIMIT clause to SELECT statement using database-specific syntax.
     *
     * <p>Modifies the {@link Select} statement in-place to add pagination limiting
     * the result set to the specified row count.
     *
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li>MySQL/PostgreSQL: Adds {@code LIMIT n} clause</li>
     *   <li>Oracle: Wraps query in subquery with {@code WHERE ROWNUM <= n}</li>
     *   <li>SQL Server: Adds {@code TOP n} clause</li>
     * </ul>
     *
     * @param select SELECT statement to modify (will be modified in-place)
     * @param limit  Maximum number of rows to return (must be positive)
     */
    void applyLimit(Select select, long limit);

    /**
     * Returns the database type identifier for this dialect.
     *
     * <p>Used for logging and debugging. Should match database product name
     * (e.g., "MySQL", "Oracle", "PostgreSQL", "Microsoft SQL Server").
     *
     * @return Database type string (e.g., "MySQL")
     */
    String getDatabaseType();
}

