package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Inspector for extracting conditions from QueryWrapper/LambdaQueryWrapper.
 *
 * <h2>Purpose</h2>
 * <p>Uses MyBatis-Plus public APIs to:
 * <ul>
 *   <li>Extract WHERE conditions</li>
 *   <li>Detect empty wrappers (critical for no-condition validation)</li>
 *   <li>Analyze complex condition trees</li>
 * </ul>
 *
 * <h2>Empty Wrapper Detection</h2>
 * <p>Empty wrapper = no WHERE conditions = dangerous (selects all rows).
 * This is critical for {@code NoWhereClauseChecker} integration.
 *
 * @since 1.1.0
 */
public final class QueryWrapperInspector {

    private static final Logger log = LoggerFactory.getLogger(QueryWrapperInspector.class);

    /**
     * Common keys for wrapper in parameter map.
     */
    private static final String[] WRAPPER_KEYS = {"ew", "wrapper", "Wrapper", "WRAPPER", "queryWrapper"};

    /**
     * Private constructor to prevent instantiation.
     */
    private QueryWrapperInspector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Detects wrapper from method parameter.
     *
     * @param parameter Method parameter object
     * @return Detected wrapper or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public static <T, R, C extends AbstractWrapper<T, R, C>> AbstractWrapper<T, R, C> detectWrapper(Object parameter) {
        if (parameter == null) {
            return null;
        }

        // Direct wrapper parameter
        if (parameter instanceof AbstractWrapper) {
            return (AbstractWrapper<T, R, C>) parameter;
        }

        // Wrapper in Map
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            
            // Check common keys first
            for (String key : WRAPPER_KEYS) {
                Object value = paramMap.get(key);
                if (value instanceof AbstractWrapper) {
                    log.trace("Detected wrapper from param map with key: {}", key);
                    return (AbstractWrapper<T, R, C>) value;
                }
            }

            // Check all values as fallback
            for (Object value : paramMap.values()) {
                if (value instanceof AbstractWrapper) {
                    log.trace("Detected wrapper from param map values");
                    return (AbstractWrapper<T, R, C>) value;
                }
            }
        }

        return null;
    }

    /**
     * Checks if QueryWrapper is empty (no conditions).
     *
     * <p><b>Critical for safety validation</b>: Empty wrapper means no WHERE clause,
     * which can lead to full table scans.
     *
     * @param parameter Method parameter (may be wrapper directly or Map containing wrapper)
     * @return {@code true} if wrapper has no conditions, {@code false} otherwise
     */
    public static boolean isEmpty(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = detectWrapper(parameter);
        if (wrapper == null) {
            return true; // No wrapper = empty
        }

        // Use public API: getSqlSegment() returns the WHERE conditions
        String sqlSegment = wrapper.getSqlSegment();
        return sqlSegment == null || sqlSegment.trim().isEmpty();
    }

    /**
     * Checks if the wrapper has WHERE conditions.
     *
     * @param parameter Method parameter (may be wrapper directly or Map containing wrapper)
     * @return {@code true} if wrapper has conditions, {@code false} otherwise
     */
    public static boolean hasConditions(Object parameter) {
        return !isEmpty(parameter);
    }

    /**
     * Extracts WHERE conditions from QueryWrapper.
     *
     * @param parameter Method parameter (may be wrapper directly or Map containing wrapper)
     * @return SQL condition string or {@code null} if empty
     */
    public static String extractConditions(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = detectWrapper(parameter);
        if (wrapper == null) {
            return null;
        }

        String sqlSegment = wrapper.getSqlSegment();
        return (sqlSegment != null && !sqlSegment.trim().isEmpty()) ? sqlSegment : null;
    }

    /**
     * Extracts the complete WHERE clause including "WHERE" keyword.
     *
     * @param parameter Method parameter (may be wrapper directly or Map containing wrapper)
     * @return Complete WHERE clause or {@code null} if empty
     */
    public static String extractCustomSqlSegment(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = detectWrapper(parameter);
        if (wrapper == null) {
            return null;
        }

        String customSqlSegment = wrapper.getCustomSqlSegment();
        return (customSqlSegment != null && !customSqlSegment.trim().isEmpty()) ? customSqlSegment : null;
    }

    /**
     * Gets wrapper type name for logging.
     *
     * @param parameter Method parameter
     * @return Wrapper type name or "null"
     */
    public static String getWrapperTypeName(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = detectWrapper(parameter);
        return wrapper != null ? wrapper.getClass().getSimpleName() : "null";
    }
}







