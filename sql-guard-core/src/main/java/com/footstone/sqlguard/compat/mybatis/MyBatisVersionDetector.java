package com.footstone.sqlguard.compat.mybatis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis version detector using marker class checking.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for existence of {@code org.apache.ibatis.session.ProviderMethodResolver}
 * which exists only in MyBatis 3.5.0+.
 *
 * <h2>Why Marker Class Detection?</h2>
 * <ul>
 *   <li><b>Reliable</b>: Class existence check is more reliable than version string parsing</li>
 *   <li><b>Simple</b>: Single {@code Class.forName()} call</li>
 *   <li><b>Thread-safe</b>: Static initialization ensures single detection</li>
 * </ul>
 *
 * <h2>Supported Versions</h2>
 * <ul>
 *   <li>MyBatis 3.4.x (3.4.6 LTS) - detected as "legacy"</li>
 *   <li>MyBatis 3.5.x (3.5.6, 3.5.13, 3.5.16) - detected as "modern"</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * if (MyBatisVersionDetector.is35OrAbove()) {
 *     // Use MyBatis 3.5.x API
 * } else {
 *     // Use MyBatis 3.4.x API
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
public final class MyBatisVersionDetector {

    private static final Logger log = LoggerFactory.getLogger(MyBatisVersionDetector.class);

    /**
     * Marker class existing only in MyBatis 3.5.0+.
     *
     * <p>{@code ProviderMethodResolver} interface was introduced in MyBatis 3.5.0
     * for enhanced @SelectProvider/@InsertProvider/@UpdateProvider/@DeleteProvider support.
     * Located in {@code org.apache.ibatis.builder.annotation} package.
     */
    private static final String MARKER_CLASS_35 =
        "org.apache.ibatis.builder.annotation.ProviderMethodResolver";

    /**
     * Cached detection result.
     *
     * <p>Static initialization ensures thread-safe single detection.
     */
    private static final boolean IS_35_OR_ABOVE = detectVersion();

    /**
     * Detected MyBatis version string for logging purposes.
     */
    private static final String DETECTED_VERSION = IS_35_OR_ABOVE ? "3.5.x" : "3.4.x";

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private MyBatisVersionDetector() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Checks if MyBatis version is 3.5.0 or above.
     *
     * <p>This method is thread-safe and returns a cached result from static initialization.
     *
     * @return {@code true} if MyBatis 3.5.0+, {@code false} if MyBatis 3.4.x
     */
    public static boolean is35OrAbove() {
        return IS_35_OR_ABOVE;
    }

    /**
     * Checks if MyBatis version is 3.4.x (legacy).
     *
     * <p>Convenience method equivalent to {@code !is35OrAbove()}.
     *
     * @return {@code true} if MyBatis 3.4.x, {@code false} if MyBatis 3.5.0+
     */
    public static boolean is34x() {
        return !IS_35_OR_ABOVE;
    }

    /**
     * Returns the detected MyBatis version category.
     *
     * @return "3.5.x" for MyBatis 3.5.0+, "3.4.x" for older versions
     */
    public static String getDetectedVersion() {
        return DETECTED_VERSION;
    }

    /**
     * Detects MyBatis version using marker class checking.
     *
     * <p>Attempts to load {@link #MARKER_CLASS_35} using {@code Class.forName()}.
     * If the class exists, MyBatis 3.5.0+ is detected; otherwise, MyBatis 3.4.x is assumed.
     *
     * @return {@code true} if marker class exists (3.5.0+), {@code false} otherwise
     */
    private static boolean detectVersion() {
        try {
            Class.forName(MARKER_CLASS_35);
            log.info("MyBatis version detected: 3.5.x (marker class {} found)", MARKER_CLASS_35);
            return true;  // MyBatis 3.5.0+
        } catch (ClassNotFoundException e) {
            log.info("MyBatis version detected: 3.4.x (marker class {} not found)", MARKER_CLASS_35);
            return false; // MyBatis 3.4.x
        }
    }
}

