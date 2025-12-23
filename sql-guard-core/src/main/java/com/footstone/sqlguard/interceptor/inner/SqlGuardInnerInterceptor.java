package com.footstone.sqlguard.interceptor.inner;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

/**
 * Inner interceptor interface for SQL Guard.
 *
 * <p>This interface follows the MyBatis-Plus InnerInterceptor pattern, allowing
 * multiple interceptors to be chained together with priority-based execution order.
 *
 * <h2>Lifecycle Methods</h2>
 *
 * <p>Each interceptor can implement lifecycle methods for different SQL operations:
 * <ul>
 *   <li><b>willDoQuery()</b>: Called before query execution. Return false to skip query.</li>
 *   <li><b>beforeQuery()</b>: Called after willDoQuery(). Can modify BoundSql.</li>
 *   <li><b>willDoUpdate()</b>: Called before INSERT/UPDATE/DELETE execution. Return false to skip.</li>
 *   <li><b>beforeUpdate()</b>: Called after willDoUpdate(). Can modify BoundSql.</li>
 * </ul>
 *
 * <h2>Priority Mechanism</h2>
 *
 * <p>Interceptors are executed in priority order (lower number = higher priority):
 * <ul>
 *   <li><b>1-99</b>: Check interceptors (e.g., SqlGuardCheckInnerInterceptor priority = 10)</li>
 *   <li><b>100-199</b>: Fallback interceptors (e.g., SelectLimitInnerInterceptor priority = 100)</li>
 *   <li><b>200+</b>: Rewrite interceptors (custom SQL rewriters)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * public class MyCustomInterceptor implements SqlGuardInnerInterceptor {
 *
 *     @Override
 *     public boolean willDoQuery(Executor executor, MappedStatement ms,
 *                               Object parameter, RowBounds rowBounds,
 *                               ResultHandler resultHandler, BoundSql boundSql)
 *             throws SQLException {
 *         // Check if query should be allowed
 *         if (shouldBlock(boundSql.getSql())) {
 *             throw new SQLException("Query blocked by custom interceptor");
 *         }
 *         return true;  // Continue to next interceptor
 *     }
 *
 *     @Override
 *     public void beforeQuery(Executor executor, MappedStatement ms,
 *                            Object parameter, RowBounds rowBounds,
 *                            ResultHandler resultHandler, BoundSql boundSql)
 *             throws SQLException {
 *         // Modify SQL or log query
 *         String sql = boundSql.getSql();
 *         System.out.println("Executing query: " + sql);
 *     }
 *
 *     @Override
 *     public int getPriority() {
 *         return 20;  // Execute after SqlGuardCheckInnerInterceptor (priority 10)
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with SqlGuardInterceptor</h2>
 *
 * <p>The main {@code SqlGuardInterceptor} orchestrates all registered InnerInterceptors:
 * <ol>
 *   <li>Sort InnerInterceptors by priority (ascending order)</li>
 *   <li>For query operations:
 *     <ul>
 *       <li>Invoke {@code willDoQuery()} on each interceptor (stop if any returns false)</li>
 *       <li>Invoke {@code beforeQuery()} on each interceptor (modifies BoundSql if needed)</li>
 *     </ul>
 *   </li>
 *   <li>For update operations (INSERT/UPDATE/DELETE):
 *     <ul>
 *       <li>Invoke {@code willDoUpdate()} on each interceptor (stop if any returns false)</li>
 *       <li>Invoke {@code beforeUpdate()} on each interceptor (modifies BoundSql if needed)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>BoundSql Modification Guidelines</h2>
 *
 * <p><b>Modifiable content:</b>
 * <ul>
 *   <li>SQL string (via reflection on {@code BoundSql.sql} field)</li>
 *   <li>Additional parameters (via {@code BoundSql.setAdditionalParameter()})</li>
 * </ul>
 *
 * <p><b>Non-modifiable content:</b>
 * <ul>
 *   <li>ParameterMappings (modifications may cause parameter binding errors)</li>
 *   <li>ParameterObject (read-only)</li>
 * </ul>
 *
 * @since 1.1.0
 * @see org.apache.ibatis.executor.Executor
 * @see org.apache.ibatis.mapping.MappedStatement
 * @see org.apache.ibatis.mapping.BoundSql
 */
public interface SqlGuardInnerInterceptor {

    /**
     * Determines whether to proceed with query execution.
     *
     * <p>This method is called before {@link #beforeQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)}.
     * If this method returns {@code false}, the query execution will be skipped and no further
     * interceptors will be invoked.
     *
     * <p><b>Typical Use Cases:</b>
     * <ul>
     *   <li>SQL safety validation (block dangerous queries)</li>
     *   <li>Permission checks</li>
     *   <li>Query caching (return cached result, skip actual query)</li>
     * </ul>
     *
     * @param executor      MyBatis Executor responsible for executing the statement
     * @param ms            MappedStatement containing SQL mapping information
     * @param parameter     Query parameter object (may be null, Map, or POJO)
     * @param rowBounds     RowBounds for pagination (may contain offset/limit)
     * @param resultHandler Result handler for processing query results
     * @param boundSql      BoundSql containing SQL string and parameter mappings
     * @return {@code true} to continue query execution, {@code false} to skip
     * @throws SQLException if an error occurs during pre-check or validation fails
     */
    default boolean willDoQuery(Executor executor, MappedStatement ms,
                               Object parameter, RowBounds rowBounds,
                               ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true;
    }

    /**
     * Modifies or validates the query before execution.
     *
     * <p>This method is called after {@link #willDoQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)}
     * returns {@code true}. Interceptors can modify the SQL by updating the BoundSql object
     * or perform additional validation.
     *
     * <p><b>Typical Use Cases:</b>
     * <ul>
     *   <li>Add automatic LIMIT clause to unbounded queries</li>
     *   <li>Add tenant filters to SQL</li>
     *   <li>SQL rewriting for optimization</li>
     *   <li>Query logging and auditing</li>
     * </ul>
     *
     * <p><b>Note:</b> To modify SQL, use reflection to update the {@code sql} field
     * in BoundSql. Be cautious as improper modifications may cause parameter binding errors.
     *
     * @param executor      MyBatis Executor responsible for executing the statement
     * @param ms            MappedStatement containing SQL mapping information
     * @param parameter     Query parameter object (may be null, Map, or POJO)
     * @param rowBounds     RowBounds for pagination (may contain offset/limit)
     * @param resultHandler Result handler for processing query results
     * @param boundSql      BoundSql containing SQL string and parameter mappings
     * @throws SQLException if validation fails or an error occurs during modification
     */
    default void beforeQuery(Executor executor, MappedStatement ms,
                            Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // Default: no-op
    }

    /**
     * Determines whether to proceed with update execution (INSERT/UPDATE/DELETE).
     *
     * <p>This method is called before {@link #beforeUpdate(Executor, MappedStatement, Object)}.
     * If this method returns {@code false}, the update execution will be skipped and no further
     * interceptors will be invoked.
     *
     * <p><b>Typical Use Cases:</b>
     * <ul>
     *   <li>Prevent UPDATE/DELETE without WHERE clause</li>
     *   <li>Block modifications to blacklisted tables</li>
     *   <li>Permission checks for write operations</li>
     * </ul>
     *
     * @param executor  MyBatis Executor responsible for executing the statement
     * @param ms        MappedStatement containing SQL mapping information
     * @param parameter Update parameter object (may be null, Map, or POJO)
     * @return {@code true} to continue update execution, {@code false} to skip
     * @throws SQLException if an error occurs during pre-check or validation fails
     */
    default boolean willDoUpdate(Executor executor, MappedStatement ms,
                                 Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * Modifies or validates the update SQL before execution.
     *
     * <p>This method is called after {@link #willDoUpdate(Executor, MappedStatement, Object)}
     * returns {@code true}. Interceptors can modify the SQL or perform additional validation.
     *
     * <p><b>Typical Use Cases:</b>
     * <ul>
     *   <li>Add audit columns (created_at, updated_by, etc.)</li>
     *   <li>Add tenant filters to UPDATE/DELETE</li>
     *   <li>SQL rewriting for soft-delete support</li>
     *   <li>Mutation logging and auditing</li>
     * </ul>
     *
     * @param executor  MyBatis Executor responsible for executing the statement
     * @param ms        MappedStatement containing SQL mapping information
     * @param parameter Update parameter object (may be null, Map, or POJO)
     * @throws SQLException if validation fails or an error occurs during modification
     */
    default void beforeUpdate(Executor executor, MappedStatement ms,
                             Object parameter)
            throws SQLException {
        // Default: no-op
    }

    /**
     * Returns the priority of this interceptor.
     *
     * <p>Lower priority number means higher execution priority (executes first).
     * Interceptors are sorted by priority in ascending order before execution.
     *
     * <h3>Priority Ranges:</h3>
     * <table border="1" style="border-collapse: collapse;">
     *   <tr><th>Range</th><th>Category</th><th>Example</th></tr>
     *   <tr><td>1-99</td><td>Check interceptors</td><td>SqlGuardCheckInnerInterceptor = 10</td></tr>
     *   <tr><td>100-199</td><td>Fallback interceptors</td><td>SelectLimitInnerInterceptor = 100</td></tr>
     *   <tr><td>200+</td><td>Rewrite interceptors</td><td>Custom SQL rewriters</td></tr>
     * </table>
     *
     * <p><b>Design Rationale:</b> Check interceptors run first to ensure security
     * validations complete before any SQL modifications. Fallback interceptors
     * provide safety nets (e.g., automatic LIMIT). Rewrite interceptors run last
     * for general SQL transformations.
     *
     * @return priority number (default: 50)
     */
    default int getPriority() {
        return 50;
    }
}

