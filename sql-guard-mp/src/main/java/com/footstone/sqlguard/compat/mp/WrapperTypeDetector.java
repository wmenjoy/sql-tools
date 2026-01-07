package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Detector for identifying wrapper types.
 *
 * <h2>Supported Types</h2>
 * <ul>
 *   <li>{@link QueryWrapper} - Standard query wrapper</li>
 *   <li>{@link LambdaQueryWrapper} - Lambda-based query wrapper</li>
 *   <li>{@link UpdateWrapper} - Standard update wrapper</li>
 *   <li>{@link LambdaUpdateWrapper} - Lambda-based update wrapper</li>
 * </ul>
 *
 * @since 1.1.0
 */
public final class WrapperTypeDetector {

    private static final Logger log = LoggerFactory.getLogger(WrapperTypeDetector.class);

    /**
     * Common keys for wrapper in parameter map.
     */
    private static final String[] WRAPPER_KEYS = {"ew", "wrapper", "Wrapper", "WRAPPER", "queryWrapper"};

    /**
     * Private constructor to prevent instantiation.
     */
    private WrapperTypeDetector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Extracts wrapper from parameter (handles direct wrapper or Map).
     *
     * @param parameter Method parameter
     * @return Wrapper object or null
     */
    private static AbstractWrapper<?, ?, ?> extractWrapper(Object parameter) {
        if (parameter == null) {
            return null;
        }

        if (parameter instanceof AbstractWrapper) {
            return (AbstractWrapper<?, ?, ?>) parameter;
        }

        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            
            for (String key : WRAPPER_KEYS) {
                Object value = paramMap.get(key);
                if (value instanceof AbstractWrapper) {
                    return (AbstractWrapper<?, ?, ?>) value;
                }
            }

            for (Object value : paramMap.values()) {
                if (value instanceof AbstractWrapper) {
                    return (AbstractWrapper<?, ?, ?>) value;
                }
            }
        }

        return null;
    }

    /**
     * Checks if wrapper is QueryWrapper (standard or lambda).
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return {@code true} if QueryWrapper or LambdaQueryWrapper
     */
    public static boolean isQueryWrapper(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        return wrapper instanceof QueryWrapper || wrapper instanceof LambdaQueryWrapper;
    }

    /**
     * Checks if wrapper is UpdateWrapper (standard or lambda).
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return {@code true} if UpdateWrapper or LambdaUpdateWrapper
     */
    public static boolean isUpdateWrapper(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        return wrapper instanceof UpdateWrapper || wrapper instanceof LambdaUpdateWrapper;
    }

    /**
     * Checks if wrapper is lambda-based.
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return {@code true} if LambdaQueryWrapper or LambdaUpdateWrapper
     */
    public static boolean isLambdaWrapper(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        return wrapper instanceof LambdaQueryWrapper || wrapper instanceof LambdaUpdateWrapper;
    }

    /**
     * Checks if wrapper is standard (non-lambda) wrapper.
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return {@code true} if standard QueryWrapper or UpdateWrapper
     */
    public static boolean isStandardWrapper(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        return (wrapper instanceof QueryWrapper && !(wrapper instanceof LambdaQueryWrapper))
            || (wrapper instanceof UpdateWrapper && !(wrapper instanceof LambdaUpdateWrapper));
    }

    /**
     * Checks if parameter contains any AbstractWrapper.
     *
     * @param parameter Method parameter
     * @return {@code true} if any wrapper type is detected
     */
    public static boolean isWrapper(Object parameter) {
        return extractWrapper(parameter) != null;
    }

    /**
     * Gets wrapper type name for logging.
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return Wrapper type name (e.g., "QueryWrapper", "LambdaQueryWrapper") or "null"
     */
    public static String getTypeName(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        return wrapper != null ? wrapper.getClass().getSimpleName() : "null";
    }

    /**
     * Gets wrapper type enum for programmatic use.
     *
     * @param parameter Method parameter (wrapper or Map containing wrapper)
     * @return WrapperType enum value
     */
    public static WrapperType getType(Object parameter) {
        AbstractWrapper<?, ?, ?> wrapper = extractWrapper(parameter);
        if (wrapper == null) {
            return WrapperType.NONE;
        }

        if (wrapper instanceof LambdaQueryWrapper) {
            return WrapperType.LAMBDA_QUERY_WRAPPER;
        }
        if (wrapper instanceof QueryWrapper) {
            return WrapperType.QUERY_WRAPPER;
        }
        if (wrapper instanceof LambdaUpdateWrapper) {
            return WrapperType.LAMBDA_UPDATE_WRAPPER;
        }
        if (wrapper instanceof UpdateWrapper) {
            return WrapperType.UPDATE_WRAPPER;
        }

        return WrapperType.UNKNOWN;
    }

    /**
     * Enum representing wrapper types.
     */
    public enum WrapperType {
        /** No wrapper detected. */
        NONE,
        /** Standard QueryWrapper. */
        QUERY_WRAPPER,
        /** Lambda-based QueryWrapper. */
        LAMBDA_QUERY_WRAPPER,
        /** Standard UpdateWrapper. */
        UPDATE_WRAPPER,
        /** Lambda-based UpdateWrapper. */
        LAMBDA_UPDATE_WRAPPER,
        /** Unknown wrapper type (AbstractWrapper subclass). */
        UNKNOWN;

        /**
         * Checks if this is a query wrapper type.
         * @return {@code true} if QUERY_WRAPPER or LAMBDA_QUERY_WRAPPER
         */
        public boolean isQuery() {
            return this == QUERY_WRAPPER || this == LAMBDA_QUERY_WRAPPER;
        }

        /**
         * Checks if this is an update wrapper type.
         * @return {@code true} if UPDATE_WRAPPER or LAMBDA_UPDATE_WRAPPER
         */
        public boolean isUpdate() {
            return this == UPDATE_WRAPPER || this == LAMBDA_UPDATE_WRAPPER;
        }

        /**
         * Checks if this is a lambda wrapper type.
         * @return {@code true} if LAMBDA_QUERY_WRAPPER or LAMBDA_UPDATE_WRAPPER
         */
        public boolean isLambda() {
            return this == LAMBDA_QUERY_WRAPPER || this == LAMBDA_UPDATE_WRAPPER;
        }
    }
}








