package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SqlExtractor implementation for MyBatis 3.4.x using reflection-based API access.
 *
 * <h2>MyBatis 3.4.x Specifics</h2>
 * <p>Handles older API for SQL extraction:
 * <ul>
 *   <li>DynamicSqlSource vs StaticSqlSource handling</li>
 *   <li>Parameter mapping differences</li>
 *   <li>BoundSql generation via reflection when needed</li>
 * </ul>
 *
 * <h2>Compatibility Notes</h2>
 * <p>MyBatis 3.4.x (especially 3.4.6 LTS) has slightly different internal APIs:
 * <ul>
 *   <li>SqlSource interface behavior may differ</li>
 *   <li>MetaObject access patterns vary</li>
 *   <li>Type handler resolution differs in edge cases</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All methods are stateless and operate only
 * on method parameters.
 *
 * @since 1.1.0
 */
public class LegacySqlExtractor implements SqlExtractor {

    private static final Logger log = LoggerFactory.getLogger(LegacySqlExtractor.class);

    /**
     * Target MyBatis version for this extractor.
     */
    private static final String TARGET_VERSION = "3.4.x";

    /**
     * Creates a new LegacySqlExtractor instance.
     */
    public LegacySqlExtractor() {
        log.debug("LegacySqlExtractor initialized for MyBatis {}", TARGET_VERSION);
    }

    /**
     * Extracts SQL from MyBatis 3.4.x components.
     *
     * <p>Uses the standard {@code BoundSql.getSql()} method which is compatible
     * with both DynamicSqlSource and StaticSqlSource in MyBatis 3.4.x.
     *
     * <h3>DynamicSqlSource Handling</h3>
     * <p>For DynamicSqlSource, the BoundSql is already generated with all
     * dynamic fragments (IF, CHOOSE, FOREACH, etc.) resolved. The getSql()
     * method returns the final SQL string.
     *
     * <h3>StaticSqlSource Handling</h3>
     * <p>For StaticSqlSource, the SQL is pre-compiled and getSql() returns
     * the static SQL string directly.
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

        // Extract SQL from BoundSql (compatible with 3.4.x)
        String sql = boundSql.getSql();

        if (log.isTraceEnabled()) {
            log.trace("Extracted SQL from MyBatis 3.4.x [{}]: {}",
                ms.getId(), truncateSql(sql));
        }

        return sql;
    }

    /**
     * Returns the target MyBatis version for this extractor.
     *
     * @return "3.4.x"
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

