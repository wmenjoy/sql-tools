package com.footstone.sqlguard.rewriter;

import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.statement.Statement;

/**
 * Interface for SQL statement rewriters providing custom SQL modification capabilities.
 *
 * <h2>Purpose</h2>
 * <p>Enables custom SQL transformations such as:
 * <ul>
 *   <li><b>Tenant Isolation:</b> Adding {@code WHERE tenant_id = ?} clauses</li>
 *   <li><b>Soft Delete:</b> Adding {@code WHERE deleted = 0} filters</li>
 *   <li><b>Audit Columns:</b> Forcing {@code ORDER BY update_time DESC}</li>
 *   <li><b>Column Masking:</b> Replacing sensitive columns with masked values</li>
 * </ul>
 *
 * <h2>Chain Rewrite Support</h2>
 * <p>Multiple rewriters can be chained via {@code SqlGuardRewriteInnerInterceptor}.
 * Each rewriter receives the Statement modified by previous rewriters:
 * <pre>
 * Original SQL -&gt; Rewriter1 -&gt; Rewriter2 -&gt; Rewriter3 -&gt; Final SQL
 * </pre>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class TenantIsolationRewriter implements StatementRewriter {
 *     @Override
 *     public Statement rewrite(Statement statement, SqlContext context) {
 *         if (statement instanceof Select) {
 *             Select select = (Select) statement;
 *             // Add WHERE tenant_id = ? clause
 *             // ...
 *             return select; // Return modified Statement
 *         }
 *         return statement; // No modification
 *     }
 *
 *     @Override
 *     public boolean isEnabled() {
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @see com.footstone.sqlguard.interceptor.inner.impl.SqlGuardRewriteInnerInterceptor
 * @since 1.1.0
 */
public interface StatementRewriter {

    /**
     * Rewrites the SQL Statement, returning a new Statement if modified.
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Return new Statement if modification is needed</li>
     *   <li>Return original Statement unchanged if no rewrite required</li>
     *   <li>Never return {@code null}</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Implementation must be thread-safe as rewriters
     * are shared across requests.
     *
     * @param statement Original Statement (or modified by previous rewriters in chain)
     * @param context   SqlContext containing SQL metadata (statement ID, mapperId, etc.)
     * @return Rewritten Statement (new instance if modified, or original if unchanged)
     * @throws RuntimeException If rewrite fails (will be logged and propagated)
     */
    Statement rewrite(Statement statement, SqlContext context);

    /**
     * Checks if this rewriter is enabled.
     *
     * <p>Disabled rewriters are skipped in the rewrite chain, improving performance
     * when certain rewrites are conditionally disabled (e.g., tenant isolation
     * disabled for admin users).
     *
     * @return {@code true} if rewriter should be invoked, {@code false} to skip
     */
    boolean isEnabled();
}

