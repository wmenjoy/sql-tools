package com.footstone.sqlguard.interceptor.jdbc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for generating unique statementId across JDBC interceptors.
 *
 * <p>StatementIdGenerator provides consistent statementId generation strategy
 * for all JDBC-layer interceptors (Druid, HikariCP, P6Spy). This ensures
 * uniform identification of SQL executions across different connection pools.</p>
 *
 * <h2>StatementId Format</h2>
 * <p>Generated statementId follows the pattern:</p>
 * <pre>
 * jdbc.{interceptorType}:{datasource}:{sqlHash}
 * </pre>
 *
 * <h2>Examples</h2>
 * <pre>
 * jdbc.druid:masterDB:a3f4b2c1   // Druid datasource "masterDB", SQL hash a3f4b2c1
 * jdbc.hikari:slaveDB:7d8e9f1a   // HikariCP datasource "slaveDB", SQL hash 7d8e9f1a
 * jdbc.p6spy:default:2b3c4d5e    // P6Spy default datasource, SQL hash 2b3c4d5e
 * </pre>
 *
 * <h2>SQL Hash Algorithm</h2>
 * <p>Uses the first 4 bytes of MD5 hash (8 hex characters) for:</p>
 * <ul>
 *   <li>Balance between uniqueness and readability</li>
 *   <li>Collision probability ~1 in 4 billion (acceptable for production)</li>
 *   <li>Short enough for logging and database indexes</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is stateless and thread-safe. All methods are static.</p>
 *
 * @since 2.0.0
 */
public final class StatementIdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(StatementIdGenerator.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private StatementIdGenerator() {
        // Utility class
    }

    /**
     * Generates unique statementId for JDBC interceptors.
     *
     * <p>Format: {@code jdbc.{interceptorType}:{datasource}:{sqlHash}}</p>
     *
     * @param interceptorType the interceptor type (e.g., "druid", "hikari", "p6spy")
     * @param datasourceName the datasource name
     * @param sql the SQL statement
     * @return unique statementId
     */
    public static String generate(String interceptorType, String datasourceName, String sql) {
        String effectiveInterceptorType = sanitize(interceptorType, "jdbc");
        String effectiveDatasource = sanitize(datasourceName, "default");
        String sqlHash = generateShortHash(sql);

        return String.format("jdbc.%s:%s:%s",
                effectiveInterceptorType,
                effectiveDatasource,
                sqlHash);
    }

    /**
     * Generates unique statementId using default "jdbc" interceptor type.
     *
     * <p>Format: {@code jdbc.jdbc:{datasource}:{sqlHash}}</p>
     *
     * @param datasourceName the datasource name
     * @param sql the SQL statement
     * @return unique statementId
     */
    public static String generate(String datasourceName, String sql) {
        return generate("jdbc", datasourceName, sql);
    }

    /**
     * Generates a short hash (8 hex characters) from SQL statement.
     *
     * <p>Uses the first 4 bytes of MD5 hash for a good balance between
     * uniqueness and readability.</p>
     *
     * <p><strong>Collision Probability:</strong> Approximately 1 in 4,294,967,296 (2^32)</p>
     *
     * @param sql the SQL statement
     * @return 8-character hexadecimal hash, or "unknown" if hashing fails
     */
    public static String generateShortHash(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "unknown";
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sql.getBytes(StandardCharsets.UTF_8));

            // Take first 4 bytes, convert to 8 hex characters
            return String.format("%02x%02x%02x%02x",
                    hash[0], hash[1], hash[2], hash[3]);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("MD5 algorithm not available, using 'unknown' for SQL hash", e);
            return "unknown";
        }
    }

    /**
     * Generates full MD5 hash (32 hex characters) from SQL statement.
     *
     * <p>This method is compatible with {@link com.footstone.sqlguard.audit.AuditEvent#getSqlId()}
     * for correlation purposes.</p>
     *
     * @param sql the SQL statement
     * @return 32-character hexadecimal MD5 hash, or null if hashing fails
     */
    public static String generateFullHash(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sql.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not available", e);
            return null;
        }
    }

    /**
     * Sanitizes input string for use in statementId.
     *
     * <p>Returns defaultValue if input is null or empty.</p>
     *
     * @param value the value to sanitize
     * @param defaultValue the default value if input is invalid
     * @return sanitized value or default
     */
    private static String sanitize(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}
