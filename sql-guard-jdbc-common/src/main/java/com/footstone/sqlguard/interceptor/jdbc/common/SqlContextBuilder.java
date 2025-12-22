package com.footstone.sqlguard.interceptor.jdbc.common;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;

/**
 * Utility class for building SqlContext objects from JDBC-level information.
 *
 * <p>SqlContextBuilder provides static factory methods to construct SqlContext
 * objects with appropriate defaults for JDBC interceptors. This standardizes
 * context construction across all connection pool implementations.</p>
 *
 * <h2>SQL Type Detection</h2>
 * <p>The builder automatically detects SQL command type from the SQL prefix:</p>
 * <ul>
 *   <li>{@code SELECT ...} → {@link SqlCommandType#SELECT}</li>
 *   <li>{@code INSERT ...} → {@link SqlCommandType#INSERT}</li>
 *   <li>{@code UPDATE ...} → {@link SqlCommandType#UPDATE}</li>
 *   <li>{@code DELETE ...} → {@link SqlCommandType#DELETE}</li>
 *   <li>Other → {@link SqlCommandType#UNKNOWN}</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Build context from JDBC metadata
 * SqlContext context = SqlContextBuilder.buildContext(
 *     "SELECT * FROM users WHERE id = ?",
 *     new Object[]{1},
 *     "masterDB"
 * );
 *
 * // Or with builder pattern
 * SqlContext context = SqlContextBuilder.builder()
 *     .sql("SELECT * FROM users")
 *     .datasource("masterDB")
 *     .interceptorType("druid")
 *     .build();
 * }</pre>
 *
 * @since 2.0.0
 * @see SqlContext
 * @see SqlCommandType
 */
public final class SqlContextBuilder {

    /** Prefix for JDBC mapper IDs */
    private static final String JDBC_MAPPER_PREFIX = "jdbc";

    /**
     * Private constructor to prevent instantiation.
     */
    private SqlContextBuilder() {
        // Utility class
    }

    /**
     * Builds a SqlContext from JDBC-level metadata.
     *
     * <p>This method automatically:</p>
     * <ul>
     *   <li>Detects SQL command type from SQL prefix</li>
     *   <li>Generates mapper ID in format "jdbc.{interceptorType}:{datasource}"</li>
     *   <li>Sets datasource name</li>
     * </ul>
     *
     * @param sql the SQL statement
     * @param params prepared statement parameters (may be null or empty)
     * @param datasourceName the datasource name (may be null, defaults to "default")
     * @return constructed SqlContext
     * @throws IllegalArgumentException if sql is null
     */
    public static SqlContext buildContext(String sql, Object[] params, String datasourceName) {
        return buildContext(sql, params, datasourceName, "jdbc");
    }

    /**
     * Builds a SqlContext with specified interceptor type.
     *
     * @param sql the SQL statement
     * @param params prepared statement parameters (may be null or empty)
     * @param datasourceName the datasource name (may be null, defaults to "default")
     * @param interceptorType the interceptor type (e.g., "druid", "hikari", "p6spy")
     * @return constructed SqlContext
     * @throws IllegalArgumentException if sql is null
     */
    public static SqlContext buildContext(String sql, Object[] params, 
            String datasourceName, String interceptorType) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL cannot be null");
        }

        String effectiveDatasource = (datasourceName != null && !datasourceName.isEmpty()) 
            ? datasourceName : "default";
        String effectiveInterceptorType = (interceptorType != null && !interceptorType.isEmpty())
            ? interceptorType : "jdbc";

        SqlCommandType type = detectSqlType(sql);
        String mapperId = buildMapperId(effectiveInterceptorType, effectiveDatasource);

        return SqlContext.builder()
            .sql(sql)
            .type(type)
            .mapperId(mapperId)
            .datasource(effectiveDatasource)
            .build();
    }

    /**
     * Detects SQL command type from SQL prefix.
     *
     * <p>Performs case-insensitive matching on the trimmed SQL string.</p>
     *
     * @param sql the SQL statement
     * @return detected command type, or UNKNOWN if not recognized
     */
    public static SqlCommandType detectSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return SqlCommandType.UNKNOWN;
        }

        String upperSql = sql.trim().toUpperCase();

        if (upperSql.startsWith("SELECT")) {
            return SqlCommandType.SELECT;
        } else if (upperSql.startsWith("INSERT")) {
            return SqlCommandType.INSERT;
        } else if (upperSql.startsWith("UPDATE")) {
            return SqlCommandType.UPDATE;
        } else if (upperSql.startsWith("DELETE")) {
            return SqlCommandType.DELETE;
        } else {
            return SqlCommandType.UNKNOWN;
        }
    }

    /**
     * Builds a mapper ID for JDBC interceptors.
     *
     * <p>Format: "jdbc.{interceptorType}:{datasource}"</p>
     *
     * @param interceptorType the interceptor type (e.g., "druid", "hikari")
     * @param datasource the datasource name
     * @return formatted mapper ID
     */
    public static String buildMapperId(String interceptorType, String datasource) {
        return String.format("%s.%s:%s", JDBC_MAPPER_PREFIX, interceptorType, datasource);
    }

    /**
     * Creates a new builder for SqlContext with fluent API.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing SqlContext objects.
     */
    public static class Builder {
        private String sql;
        private Object[] params;
        private String datasource = "default";
        private String interceptorType = "jdbc";
        private SqlCommandType type;

        /**
         * Sets the SQL statement.
         *
         * @param sql the SQL statement
         * @return this builder
         */
        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        /**
         * Sets the prepared statement parameters.
         *
         * @param params the parameters
         * @return this builder
         */
        public Builder params(Object... params) {
            this.params = params;
            return this;
        }

        /**
         * Sets the datasource name.
         *
         * @param datasource the datasource name
         * @return this builder
         */
        public Builder datasource(String datasource) {
            this.datasource = datasource;
            return this;
        }

        /**
         * Sets the interceptor type.
         *
         * @param interceptorType the interceptor type (e.g., "druid", "hikari", "p6spy")
         * @return this builder
         */
        public Builder interceptorType(String interceptorType) {
            this.interceptorType = interceptorType;
            return this;
        }

        /**
         * Sets the SQL command type explicitly.
         *
         * <p>If not set, type will be auto-detected from SQL.</p>
         *
         * @param type the command type
         * @return this builder
         */
        public Builder type(SqlCommandType type) {
            this.type = type;
            return this;
        }

        /**
         * Builds the SqlContext.
         *
         * @return constructed SqlContext
         * @throws IllegalArgumentException if sql is null
         */
        public SqlContext build() {
            if (sql == null) {
                throw new IllegalArgumentException("SQL cannot be null");
            }

            SqlCommandType effectiveType = (type != null) ? type : detectSqlType(sql);
            String mapperId = buildMapperId(interceptorType, datasource);

            return SqlContext.builder()
                .sql(sql)
                .type(effectiveType)
                .mapperId(mapperId)
                .datasource(datasource)
                .build();
        }
    }
}

