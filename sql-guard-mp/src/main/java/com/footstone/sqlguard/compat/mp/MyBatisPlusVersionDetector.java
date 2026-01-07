package com.footstone.sqlguard.compat.mp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis-Plus version detector using marker class checking.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for existence of version-specific classes to determine
 * whether MyBatis-Plus 3.5.x or 3.4.x is being used.
 *
 * <h2>Supported Versions</h2>
 * <ul>
 *   <li>MyBatis-Plus 3.4.x (3.4.0, 3.4.3) - detected as "legacy"</li>
 *   <li>MyBatis-Plus 3.5.x (3.5.3, 3.5.5) - detected as "modern"</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * if (MyBatisPlusVersionDetector.is35OrAbove()) {
 *     // Use MyBatis-Plus 3.5.x API
 * } else {
 *     // Use MyBatis-Plus 3.4.x API
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
public final class MyBatisPlusVersionDetector {

    private static final Logger log = LoggerFactory.getLogger(MyBatisPlusVersionDetector.class);

    /**
     * Marker class existing only in MyBatis-Plus 3.5.0+.
     * LambdaMeta was introduced in 3.5.0 for improved lambda support.
     */
    private static final String MARKER_CLASS_35 =
        "com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta";

    /**
     * Cached detection result.
     * Static initialization ensures thread-safe single detection.
     */
    private static final boolean IS_35_OR_ABOVE = detectVersion();

    /**
     * Detected MyBatis-Plus version string for logging purposes.
     */
    private static final String DETECTED_VERSION = IS_35_OR_ABOVE ? "3.5.x" : "3.4.x";

    /**
     * Private constructor to prevent instantiation.
     */
    private MyBatisPlusVersionDetector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Checks if MyBatis-Plus version is 3.5.0 or above.
     *
     * @return {@code true} if MyBatis-Plus 3.5.0+, {@code false} if MyBatis-Plus 3.4.x
     */
    public static boolean is35OrAbove() {
        return IS_35_OR_ABOVE;
    }

    /**
     * Checks if MyBatis-Plus version is 3.4.x (legacy).
     *
     * @return {@code true} if MyBatis-Plus 3.4.x, {@code false} if MyBatis-Plus 3.5.0+
     */
    public static boolean is34x() {
        return !IS_35_OR_ABOVE;
    }

    /**
     * Returns the detected MyBatis-Plus version category.
     *
     * @return "3.5.x" for MyBatis-Plus 3.5.0+, "3.4.x" for older versions
     */
    public static String getDetectedVersion() {
        return DETECTED_VERSION;
    }

    /**
     * Detects MyBatis-Plus version using marker class checking.
     *
     * @return {@code true} if marker class exists (3.5.0+), {@code false} otherwise
     */
    private static boolean detectVersion() {
        try {
            Class.forName(MARKER_CLASS_35);
            log.info("MyBatis-Plus version detected: 3.5.x (marker class {} found)", MARKER_CLASS_35);
            return true;
        } catch (ClassNotFoundException e) {
            log.info("MyBatis-Plus version detected: 3.4.x (marker class {} not found)", MARKER_CLASS_35);
            return false;
        }
    }
}








