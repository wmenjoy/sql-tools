package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * Interface for version-agnostic SQL extraction from MyBatis components.
 *
 * <h2>Purpose</h2>
 * <p>Abstracts differences in SQL extraction between MyBatis 3.4.x and 3.5.x:
 * <ul>
 *   <li>MyBatis 3.4.x: Uses {@link LegacySqlExtractor} with 3.4.x-specific API</li>
 *   <li>MyBatis 3.5.x: Uses {@link ModernSqlExtractor} with 3.5.x-specific API</li>
 * </ul>
 *
 * <h2>API Differences Between Versions</h2>
 * <table border="1" style="border-collapse: collapse;">
 *   <tr><th>Aspect</th><th>MyBatis 3.4.x</th><th>MyBatis 3.5.x</th></tr>
 *   <tr><td>BoundSql generation</td><td>Older API</td><td>Improved API</td></tr>
 *   <tr><td>Parameter handling</td><td>Basic</td><td>Enhanced type safety</td></tr>
 *   <tr><td>DynamicSqlSource</td><td>Special handling needed</td><td>More unified</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * SqlExtractor extractor = SqlExtractorFactory.create();
 * String sql = extractor.extractSql(mappedStatement, parameter, boundSql);
 * 
 * // Use extracted SQL for validation or logging
 * if (sql.contains("SELECT *")) {
 *     log.warn("SELECT * detected in SQL: {}", sql);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe. The {@link SqlExtractorFactory} caches
 * a single instance for reuse across threads.
 *
 * @see LegacySqlExtractor
 * @see ModernSqlExtractor
 * @see SqlExtractorFactory
 * @since 1.1.0
 */
public interface SqlExtractor {

    /**
     * Extracts SQL string from MappedStatement and BoundSql.
     *
     * <p>Handles differences in parameter mapping and SQL generation between
     * MyBatis versions. The extracted SQL is the final SQL string that will
     * be executed against the database (with placeholders, not actual values).
     *
     * <h3>SQL Content</h3>
     * <p>The returned SQL contains:
     * <ul>
     *   <li>Complete SQL statement (SELECT, INSERT, UPDATE, DELETE, etc.)</li>
     *   <li>Parameter placeholders ({@code ?}) for prepared statement binding</li>
     *   <li>Dynamic SQL fragments already resolved (IF, CHOOSE, FOREACH, etc.)</li>
     * </ul>
     *
     * <h3>Null Handling</h3>
     * <ul>
     *   <li>{@code ms}: Required, must not be null</li>
     *   <li>{@code parameter}: May be null for queries without parameters</li>
     *   <li>{@code boundSql}: Required, must not be null</li>
     * </ul>
     *
     * @param ms        MappedStatement containing SQL metadata (statement ID, SQL source, etc.)
     * @param parameter SQL parameter object (may be null, Map, POJO, or primitive wrapper)
     * @param boundSql  BoundSql containing generated SQL and parameter mappings
     * @return Extracted SQL string, never null
     * @throws IllegalArgumentException if ms or boundSql is null
     */
    String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql);

    /**
     * Returns the MyBatis version this extractor is designed for.
     *
     * @return "3.4.x" for legacy extractor, "3.5.x" for modern extractor
     */
    default String getTargetVersion() {
        return "unknown";
    }
}








