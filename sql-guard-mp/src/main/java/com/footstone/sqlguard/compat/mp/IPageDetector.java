package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Detector for IPage pagination in method parameters.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for IPage in two scenarios:
 * <ol>
 *   <li>Direct parameter: {@code parameter instanceof IPage}</li>
 *   <li>Wrapped in Map: {@code ((Map)parameter).get("page") instanceof IPage}</li>
 * </ol>
 *
 * <h2>Supported Scenarios</h2>
 * <ul>
 *   <li>Direct IPage parameter: {@code List<User> selectPage(IPage<User> page, ...)}</li>
 *   <li>Map-wrapped IPage: {@code @Param("page") IPage<User> page, @Param("ew") QueryWrapper<User> wrapper}</li>
 * </ul>
 *
 * @since 1.1.0
 */
public final class IPageDetector {

    private static final Logger log = LoggerFactory.getLogger(IPageDetector.class);

    /**
     * Common keys for IPage in parameter map.
     */
    private static final String[] PAGE_KEYS = {"page", "Page", "PAGE", "ipage", "IPage"};

    /**
     * Private constructor to prevent instantiation.
     */
    private IPageDetector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Detects IPage from method parameter.
     *
     * @param parameter Method parameter object
     * @return Detected IPage or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public static <T> IPage<T> detect(Object parameter) {
        if (parameter == null) {
            return null;
        }

        // Scenario 1: Direct IPage parameter
        if (parameter instanceof IPage) {
            log.trace("Detected direct IPage parameter");
            return (IPage<T>) parameter;
        }

        // Scenario 2: IPage wrapped in Map
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            
            // Check common keys first
            for (String key : PAGE_KEYS) {
                Object value = paramMap.get(key);
                if (value instanceof IPage) {
                    log.trace("Detected IPage from param map with key: {}", key);
                    return (IPage<T>) value;
                }
            }

            // Check all values as fallback
            for (Object value : paramMap.values()) {
                if (value instanceof IPage) {
                    log.trace("Detected IPage from param map values");
                    return (IPage<T>) value;
                }
            }
        }

        return null;
    }

    /**
     * Checks if parameter contains IPage pagination.
     *
     * @param parameter Method parameter object
     * @return {@code true} if IPage detected, {@code false} otherwise
     */
    public static boolean hasPagination(Object parameter) {
        return detect(parameter) != null;
    }

    /**
     * Extracts current page number from IPage.
     *
     * @param parameter Method parameter object
     * @return Current page number, or -1 if not found
     */
    public static long getCurrent(Object parameter) {
        IPage<?> page = detect(parameter);
        return page != null ? page.getCurrent() : -1;
    }

    /**
     * Extracts page size from IPage.
     *
     * @param parameter Method parameter object
     * @return Page size, or -1 if not found
     */
    public static long getSize(Object parameter) {
        IPage<?> page = detect(parameter);
        return page != null ? page.getSize() : -1;
    }

    /**
     * Extracts pagination info (current, size) from IPage.
     *
     * @param parameter Method parameter object
     * @return String representation of pagination info or {@code null}
     */
    public static String extractPaginationInfo(Object parameter) {
        IPage<?> page = detect(parameter);
        if (page != null) {
            return String.format("IPage(current=%d, size=%d)", page.getCurrent(), page.getSize());
        }
        return null;
    }

    /**
     * Checks if the detected IPage has valid pagination settings.
     *
     * @param parameter Method parameter object
     * @return {@code true} if IPage has positive current and size values
     */
    public static boolean hasValidPagination(Object parameter) {
        IPage<?> page = detect(parameter);
        return page != null && page.getCurrent() > 0 && page.getSize() > 0;
    }
}

