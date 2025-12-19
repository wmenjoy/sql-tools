package com.footstone.sqlguard.interceptor.jdbc.hikari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for detecting HikariCP version at runtime.
 *
 * <p>HikariVersionDetector provides reflection-based version detection to support
 * both HikariCP 4.x (Java 8+) and HikariCP 5.x (Java 11+). This allows the interceptor
 * to adapt behavior based on the actual HikariCP version on the classpath.</p>
 *
 * <h2>Version Differences</h2>
 * <ul>
 *   <li><strong>HikariCP 4.x:</strong> Java 8+, different internal pool class structure</li>
 *   <li><strong>HikariCP 5.x:</strong> Java 11+, updated API, enhanced metrics</li>
 * </ul>
 *
 * <h2>Detection Strategy</h2>
 * <p>Uses reflection to check for classes/methods that exist only in specific versions:</p>
 * <ul>
 *   <li>HikariCP 5.x: Check for new API methods added in 5.0</li>
 *   <li>HikariCP 4.x: Fallback when 5.x markers not found</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * if (HikariVersionDetector.isHikari5x()) {
 *     // Use HikariCP 5.x specific features
 * } else {
 *     // Use HikariCP 4.x compatible code
 * }
 * }</pre>
 *
 * @since 2.0.0
 */
public final class HikariVersionDetector {

    private static final Logger logger = LoggerFactory.getLogger(HikariVersionDetector.class);

    /**
     * Cached version detection result.
     */
    private static final VersionInfo VERSION_INFO;

    static {
        VERSION_INFO = detectVersion();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private HikariVersionDetector() {
        // Utility class
    }

    /**
     * Checks if HikariCP 5.x is on the classpath.
     *
     * @return true if HikariCP 5.x is detected
     */
    public static boolean isHikari5x() {
        return VERSION_INFO.majorVersion >= 5;
    }

    /**
     * Checks if HikariCP 4.x is on the classpath.
     *
     * @return true if HikariCP 4.x is detected
     */
    public static boolean isHikari4x() {
        return VERSION_INFO.majorVersion == 4;
    }

    /**
     * Gets the detected HikariCP version string.
     *
     * @return version string (e.g., "5.1.0") or "unknown" if not detected
     */
    public static String getVersion() {
        return VERSION_INFO.version;
    }

    /**
     * Gets the major version number.
     *
     * @return major version (4 or 5), or 0 if not detected
     */
    public static int getMajorVersion() {
        return VERSION_INFO.majorVersion;
    }

    /**
     * Checks if HikariCP is available on the classpath.
     *
     * @return true if HikariCP is found
     */
    public static boolean isHikariCpAvailable() {
        return VERSION_INFO.available;
    }

    /**
     * Detects HikariCP version using reflection.
     *
     * @return VersionInfo with detected version details
     */
    private static VersionInfo detectVersion() {
        try {
            // Check if HikariDataSource exists
            Class<?> dsClass = Class.forName("com.zaxxer.hikari.HikariDataSource");
            
            // Try to get version from package info or other sources
            String version = getVersionFromPackage(dsClass);
            int majorVersion = parseMajorVersion(version);
            
            // If version not found, try to detect by class structure
            if (majorVersion == 0) {
                majorVersion = detectByClassStructure();
                version = majorVersion + ".x";
            }

            logger.info("Detected HikariCP version: {} (major: {})", version, majorVersion);
            return new VersionInfo(true, version, majorVersion);

        } catch (ClassNotFoundException e) {
            logger.debug("HikariCP not found on classpath");
            return new VersionInfo(false, "unknown", 0);
        }
    }

    /**
     * Tries to get version from package implementation info.
     */
    private static String getVersionFromPackage(Class<?> clazz) {
        try {
            Package pkg = clazz.getPackage();
            if (pkg != null) {
                String implVersion = pkg.getImplementationVersion();
                if (implVersion != null && !implVersion.isEmpty()) {
                    return implVersion;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get version from package info: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Parses major version number from version string.
     */
    private static int parseMajorVersion(String version) {
        if (version == null || version.isEmpty() || "unknown".equals(version)) {
            return 0;
        }
        try {
            // Version format: "4.0.3" or "5.1.0"
            String[] parts = version.split("\\.");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (NumberFormatException e) {
            logger.debug("Could not parse version: {}", version);
        }
        return 0;
    }

    /**
     * Detects version by checking for classes/methods specific to each version.
     */
    private static int detectByClassStructure() {
        try {
            // HikariCP 5.x has some API changes we can detect
            // Try to find a method that exists only in 5.x
            Class<?> configClass = Class.forName("com.zaxxer.hikari.HikariConfig");
            
            // Check for methods that indicate 5.x
            // Note: This is a heuristic and may need adjustment
            try {
                // HikariCP 5.x requires Java 11 and has updated record-based internals
                // Check for any 5.x specific method
                configClass.getMethod("getKeepaliveTime");
                return 5;
            } catch (NoSuchMethodException e) {
                // Method not found, likely 4.x
            }

            // Default to 4.x if we can't determine
            return 4;

        } catch (ClassNotFoundException e) {
            logger.debug("Could not detect version by class structure");
            return 0;
        }
    }

    /**
     * Version information holder.
     */
    private static class VersionInfo {
        final boolean available;
        final String version;
        final int majorVersion;

        VersionInfo(boolean available, String version, int majorVersion) {
            this.available = available;
            this.version = version;
            this.majorVersion = majorVersion;
        }
    }
}
