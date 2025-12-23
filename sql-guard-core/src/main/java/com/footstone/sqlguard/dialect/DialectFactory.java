package com.footstone.sqlguard.dialect;

import com.footstone.sqlguard.dialect.impl.DB2Dialect;
import com.footstone.sqlguard.dialect.impl.DmDialect;
import com.footstone.sqlguard.dialect.impl.GaussDBDialect;
import com.footstone.sqlguard.dialect.impl.InformixDialect;
import com.footstone.sqlguard.dialect.impl.KingbaseDialect;
import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import com.footstone.sqlguard.dialect.impl.OpenGaussDialect;
import com.footstone.sqlguard.dialect.impl.OracleDialect;
import com.footstone.sqlguard.dialect.impl.OscarDialect;
import com.footstone.sqlguard.dialect.impl.PostgreSQLDialect;
import com.footstone.sqlguard.dialect.impl.SQLServerDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for auto-detecting database type and returning appropriate SqlGuardDialect.
 *
 * <h2>Detection Strategy</h2>
 * <p>Reads {@code DatabaseMetaData.getDatabaseProductName()} from JDBC Connection
 * and maps to corresponding dialect implementation.
 *
 * <h2>Caching</h2>
 * <p>Detected dialects are cached per DataSource to avoid repeated metadata queries.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Auto-detect dialect from DataSource
 * SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);
 *
 * // Or create dialect by database name
 * SqlGuardDialect mysqlDialect = DialectFactory.createDialect("MySQL");
 * }</pre>
 *
 * @since 1.1.0
 */
public class DialectFactory {

    private static final Logger log = LoggerFactory.getLogger(DialectFactory.class);

    /**
     * Cache: DataSource → SqlGuardDialect (avoid repeated detection).
     */
    private static final ConcurrentHashMap<DataSource, SqlGuardDialect> DIALECT_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation.
     */
    private DialectFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Detects database type from DataSource and returns appropriate dialect.
     *
     * <p>Detection is cached to avoid repeated JDBC metadata queries.
     *
     * @param dataSource JDBC DataSource
     * @return SqlGuardDialect for detected database, or MySQLDialect as default
     */
    public static SqlGuardDialect getDialect(DataSource dataSource) {
        if (dataSource == null) {
            log.warn("DataSource is null, defaulting to MySQL dialect");
            return new MySQLDialect();
        }

        return DIALECT_CACHE.computeIfAbsent(dataSource, ds -> {
            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String productName = metaData.getDatabaseProductName();

                log.info("Detected database: {}", productName);

                SqlGuardDialect dialect = createDialect(productName);
                log.info("Using dialect: {}", dialect.getDatabaseType());

                return dialect;
            } catch (SQLException e) {
                log.warn("Failed to detect database type, defaulting to MySQL", e);
                return new MySQLDialect(); // Default fallback
            }
        });
    }

    /**
     * Creates dialect based on database product name.
     *
     * <p>Supported database product names:
     * <ul>
     *   <li>MySQL, MariaDB → MySQLDialect</li>
     *   <li>PostgreSQL → PostgreSQLDialect</li>
     *   <li>Oracle → OracleDialect</li>
     *   <li>Microsoft SQL Server → SQLServerDialect</li>
     *   <li>DB2 → DB2Dialect</li>
     *   <li>Informix → InformixDialect</li>
     *   <li>DM (达梦) → DmDialect</li>
     *   <li>KingbaseES (金仓) → KingbaseDialect</li>
     *   <li>Oscar (神通) → OscarDialect</li>
     *   <li>GaussDB (华为) → GaussDBDialect</li>
     *   <li>openGauss → OpenGaussDialect</li>
     * </ul>
     *
     * @param productName Database product name from metadata
     * @return SqlGuardDialect instance
     */
    public static SqlGuardDialect createDialect(String productName) {
        if (productName == null || productName.isEmpty()) {
            log.warn("Database product name is null or empty, defaulting to MySQL");
            return new MySQLDialect();
        }

        String lowerName = productName.toLowerCase();

        // MySQL and MariaDB
        if (lowerName.contains("mysql") || lowerName.contains("mariadb")) {
            return new MySQLDialect();
        }
        // PostgreSQL
        if (lowerName.contains("postgresql")) {
            return new PostgreSQLDialect();
        }
        // Oracle
        if (lowerName.contains("oracle")) {
            return new OracleDialect();
        }
        // Microsoft SQL Server
        if (lowerName.contains("sql server") || lowerName.contains("microsoft")) {
            return new SQLServerDialect();
        }
        // IBM DB2
        if (lowerName.contains("db2")) {
            return new DB2Dialect();
        }
        // IBM Informix
        if (lowerName.contains("informix")) {
            return new InformixDialect();
        }
        // 达梦数据库 (DM Database)
        if (lowerName.contains("dm") || lowerName.contains("dameng") || lowerName.contains("达梦")) {
            return new DmDialect();
        }
        // 金仓数据库 (KingbaseES)
        if (lowerName.contains("kingbase") || lowerName.contains("金仓")) {
            return new KingbaseDialect();
        }
        // 神通数据库 (Oscar)
        if (lowerName.contains("oscar") || lowerName.contains("神通")) {
            return new OscarDialect();
        }
        // openGauss (检测顺序：先检测 openGauss，再检测 GaussDB)
        if (lowerName.contains("opengauss")) {
            return new OpenGaussDialect();
        }
        // 华为 GaussDB
        if (lowerName.contains("gaussdb") || lowerName.contains("gauss")) {
            return new GaussDBDialect();
        }

        log.warn("Unknown database: {}, defaulting to MySQL", productName);
        return new MySQLDialect(); // Default fallback
    }

    /**
     * Clears dialect cache (for testing).
     */
    public static void clearCache() {
        DIALECT_CACHE.clear();
    }

    /**
     * Returns the current cache size (for testing).
     *
     * @return Number of cached dialects
     */
    static int cacheSize() {
        return DIALECT_CACHE.size();
    }
}

