package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SqlExtractor implementation for MyBatis 3.5.x using improved API.
 *
 * <h2>MyBatis 3.5.x Improvements</h2>
 * <p>Leverages improved API for SQL extraction:
 * <ul>
 *   <li>Cleaner parameter handling with enhanced type safety</li>
 *   <li>Improved BoundSql generation with better caching</li>
 *   <li>More consistent behavior across SqlSource types</li>
 * </ul>
 *
 * <h2>Supported Versions</h2>
 * <ul>
 *   <li>MyBatis 3.5.6</li>
 *   <li>MyBatis 3.5.13</li>
 *   <li>MyBatis 3.5.16</li>
 * </ul>
 *
 * <h2>API Enhancements in 3.5.x</h2>
 * <p>MyBatis 3.5.x introduced several improvements:
 * <ul>
 *   <li>{@code ProviderMethodResolver} for enhanced @Provider annotations</li>
 *   <li>Improved {@code Configuration} caching mechanisms</li>
 *   <li>Better support for Java 8+ features (Optional, Stream, etc.)</li>
 *   <li>Enhanced scripting engine support</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All methods are stateless and operate only
 * on method parameters.
 *
 * @since 1.1.0
 */
public class ModernSqlExtractor implements SqlExtractor {

    private static final Logger log = LoggerFactory.getLogger(ModernSqlExtractor.class);

    /**
     * Target MyBatis version for this extractor.
     */
    private static final String TARGET_VERSION = "3.5.x";

    /**
     * Creates a new ModernSqlExtractor instance.
     */
    public ModernSqlExtractor() {
        log.debug("ModernSqlExtractor initialized for MyBatis {}", TARGET_VERSION);
    }

    /**
     * Extracts SQL from MyBatis 3.5.x components.
     *
     * <p>Uses the standard {@code BoundSql.getSql()} method with MyBatis 3.5.x
     * improved API. The 3.5.x version provides more consistent behavior across
     * different SqlSource types.
     *
     * <h3>Enhanced Parameter Handling</h3>
     * <p>MyBatis 3.5.x has improved parameter handling with:
     * <ul>
     *   <li>Better null safety in parameter mappings</li>
     *   <li>Enhanced type handler resolution</li>
     *   <li>Improved support for complex parameter types</li>
     * </ul>
     *
     * <h3>BoundSql Improvements</h3>
     * <p>The BoundSql in 3.5.x has:
     * <ul>
     *   <li>More efficient caching of generated SQL</li>
     *   <li>Better handling of additional parameters</li>
     *   <li>Improved MetaObject integration</li>
     * </ul>
     *
     * @param ms        MappedStatement containing SQL metadata
     * @param parameter SQL parameter object (may be null)
     * @param boundSql  BoundSql containing generated SQL
     * @return Extracted SQL string
     * @throws IllegalArgumentException if ms or boundSql is null
     */
    @Override
    public String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql) {
        if (ms == null) {
            throw new IllegalArgumentException("MappedStatement cannot be null");
        }
        if (boundSql == null) {
            throw new IllegalArgumentException("BoundSql cannot be null");
        }

        // Extract SQL from BoundSql (using 3.5.x improved API)
        String sql = boundSql.getSql();

        if (log.isTraceEnabled()) {
            log.trace("Extracted SQL from MyBatis 3.5.x [{}]: {}",
                ms.getId(), truncateSql(sql));
        }

        return sql;
    }

    /**
     * Returns the target MyBatis version for this extractor.
     *
     * @return "3.5.x"
     */
    @Override
    public String getTargetVersion() {
        return TARGET_VERSION;
    }

    /**
     * Truncates SQL for logging purposes.
     *
     * @param sql SQL string to truncate
     * @return Truncated SQL (max 100 chars) with ellipsis if truncated
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return "null";
        }
        if (sql.length() <= 100) {
            return sql;
        }
        return sql.substring(0, 100) + "...";
    }
}






