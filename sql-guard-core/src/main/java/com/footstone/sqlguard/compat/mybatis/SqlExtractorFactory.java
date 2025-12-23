package com.footstone.sqlguard.compat.mybatis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating version-appropriate SqlExtractor instances.
 *
 * <h2>Selection Strategy</h2>
 * <p>Uses {@link MyBatisVersionDetector} to determine MyBatis version:
 * <ul>
 *   <li>MyBatis 3.5.0+: Returns {@link ModernSqlExtractor}</li>
 *   <li>MyBatis 3.4.x: Returns {@link LegacySqlExtractor}</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * <p>Caches SqlExtractor instance using static initialization to ensure:
 * <ul>
 *   <li>Thread-safe singleton pattern</li>
 *   <li>No repeated instance creation</li>
 *   <li>Consistent behavior across all callers</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Get the appropriate SqlExtractor for current MyBatis version
 * SqlExtractor extractor = SqlExtractorFactory.create();
 * 
 * // Use extractor to get SQL
 * String sql = extractor.extractSql(mappedStatement, parameter, boundSql);
 * 
 * // Check which version was selected
 * String version = extractor.getTargetVersion();  // "3.4.x" or "3.5.x"
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This factory is thread-safe. The cached SqlExtractor instance is
 * initialized during class loading, ensuring safe concurrent access.
 *
 * @since 1.1.0
 */
public final class SqlExtractorFactory {

    private static final Logger log = LoggerFactory.getLogger(SqlExtractorFactory.class);

    /**
     * Cached SqlExtractor instance.
     *
     * <p>Static initialization ensures thread-safe singleton pattern.
     * The extractor is created once during class loading based on
     * detected MyBatis version.
     */
    private static final SqlExtractor INSTANCE = createExtractor();

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private SqlExtractorFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns cached SqlExtractor instance for current MyBatis version.
     *
     * <p>This method always returns the same SqlExtractor instance,
     * which is appropriate for the detected MyBatis version:
     * <ul>
     *   <li>{@link ModernSqlExtractor} for MyBatis 3.5.x</li>
     *   <li>{@link LegacySqlExtractor} for MyBatis 3.4.x</li>
     * </ul>
     *
     * @return SqlExtractor instance (ModernSqlExtractor for 3.5.x, LegacySqlExtractor for 3.4.x)
     */
    public static SqlExtractor create() {
        return INSTANCE;
    }

    /**
     * Returns the cached SqlExtractor instance.
     *
     * <p>Alias for {@link #create()} for more intuitive API usage.
     *
     * @return SqlExtractor instance
     */
    public static SqlExtractor getInstance() {
        return INSTANCE;
    }

    /**
     * Creates SqlExtractor based on detected MyBatis version.
     *
     * <p>Uses {@link MyBatisVersionDetector#is35OrAbove()} to determine
     * which implementation to create.
     *
     * @return SqlExtractor implementation appropriate for detected version
     */
    private static SqlExtractor createExtractor() {
        SqlExtractor extractor;
        if (MyBatisVersionDetector.is35OrAbove()) {
            extractor = new ModernSqlExtractor();
            log.info("SqlExtractorFactory: Created ModernSqlExtractor for MyBatis 3.5.x");
        } else {
            extractor = new LegacySqlExtractor();
            log.info("SqlExtractorFactory: Created LegacySqlExtractor for MyBatis 3.4.x");
        }
        return extractor;
    }

    /**
     * Returns the detected MyBatis version.
     *
     * <p>Convenience method delegating to {@link MyBatisVersionDetector#getDetectedVersion()}.
     *
     * @return "3.5.x" or "3.4.x"
     */
    public static String getDetectedVersion() {
        return MyBatisVersionDetector.getDetectedVersion();
    }

    /**
     * Checks if using modern (3.5.x) SqlExtractor.
     *
     * @return {@code true} if using ModernSqlExtractor, {@code false} if using LegacySqlExtractor
     */
    public static boolean isUsingModernExtractor() {
        return INSTANCE instanceof ModernSqlExtractor;
    }

    /**
     * Checks if using legacy (3.4.x) SqlExtractor.
     *
     * @return {@code true} if using LegacySqlExtractor, {@code false} if using ModernSqlExtractor
     */
    public static boolean isUsingLegacyExtractor() {
        return INSTANCE instanceof LegacySqlExtractor;
    }
}

