package com.footstone.sqlguard.core.model;

import java.util.Map;
import java.util.Objects;
import net.sf.jsqlparser.statement.Statement;

/**
 * Immutable context object containing SQL execution information for validation.
 * 
 * <p>SqlContext encapsulates all necessary information about a SQL statement execution,
 * including the raw SQL string, parsed AST representation, command type, and execution
 * context (mapper ID, parameters, datasource, pagination bounds).</p>
 * 
 * <p>This class uses the Builder pattern to accommodate varying contexts across different
 * SQL execution scenarios:</p>
 * <ul>
 *   <li>MyBatis XML Mapper execution (with mapperId, params, rowBounds)</li>
 *   <li>Direct JDBC execution (minimal context)</li>
 *   <li>MyBatis-Plus QueryWrapper execution (with datasource routing)</li>
 * </ul>
 * 
 * <p><strong>Immutability Guarantees:</strong></p>
 * <ul>
 *   <li>{@code statement}, {@code type}, and {@code mapperId} are final and cannot be modified
 *       after construction to prevent accidental changes during validation chain execution.</li>
 *   <li>Optional fields ({@code params}, {@code datasource}, {@code rowBounds}) can be null.</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SqlContext context = SqlContext.builder()
 *     .sql("SELECT * FROM users WHERE id = ?")
 *     .type(SqlCommandType.SELECT)
 *     .mapperId("com.example.UserMapper.selectById")
 *     .statement(parsedStatement)
 *     .params(paramMap)
 *     .build();
 * }</pre>
 *
 * @see SqlContextBuilder
 * @see SqlCommandType
 */
public final class SqlContext {

  /**
   * Raw SQL string (required).
   */
  private final String sql;

  /**
   * Parsed SQL statement AST from JSQLParser (optional).
   */
  private final Statement statement;

  /**
   * SQL command type classification (required).
   */
  private final SqlCommandType type;

  /**
   * Mapper identifier in format "namespace.methodId" (required).
   * Example: "com.example.UserMapper.selectById"
   */
  private final String mapperId;

  /**
   * Execution parameters map (optional).
   * Contains parameter names/positions mapped to values.
   */
  private final Map<String, Object> params;

  /**
   * Datasource identifier for multi-datasource routing (optional).
   */
  private final String datasource;

  /**
   * MyBatis RowBounds for pagination (optional).
   */
  private final Object rowBounds;

  /**
   * Private constructor. Use {@link #builder()} to create instances.
   */
  private SqlContext(SqlContextBuilder builder) {
    this.sql = builder.sql;
    this.statement = builder.statement;
    this.type = builder.type;
    this.mapperId = builder.mapperId;
    this.params = builder.params;
    this.datasource = builder.datasource;
    this.rowBounds = builder.rowBounds;
  }

  /**
   * Creates a new SqlContext builder.
   *
   * @return a new SqlContextBuilder instance
   */
  public static SqlContextBuilder builder() {
    return new SqlContextBuilder();
  }

  // Getters

  public String getSql() {
    return sql;
  }

  /**
   * Returns the parsed SQL statement.
   *
   * @return the parsed SQL statement, or null if not set
   */
  public Statement getStatement() {
    return statement;
  }

  public SqlCommandType getType() {
    return type;
  }

  public String getMapperId() {
    return mapperId;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public String getDatasource() {
    return datasource;
  }

  public Object getRowBounds() {
    return rowBounds;
  }

  /**
   * Builder for constructing SqlContext instances with fluent API.
   *
   * <p>Required fields: sql, type, mapperId</p>
   * <p>Optional fields: statement, params, datasource, rowBounds</p>
   */
  public static class SqlContextBuilder {
    private String sql;
    private Statement statement;
    private SqlCommandType type;
    private String mapperId;
    private Map<String, Object> params;
    private String datasource;
    private Object rowBounds;

    private SqlContextBuilder() {
    }

    /**
     * Sets the raw SQL string (required).
     *
     * @param sql the SQL string
     * @return this builder
     */
    public SqlContextBuilder sql(String sql) {
      this.sql = sql;
      return this;
    }

    /**
     * Sets the parsed SQL statement AST.
     *
     * @param statement the parsed Statement from JSQLParser
     * @return this builder
     */
    public SqlContextBuilder statement(Statement statement) {
      this.statement = statement;
      return this;
    }

    /**
     * Sets the SQL command type (required).
     *
     * @param type the SqlCommandType
     * @return this builder
     */
    public SqlContextBuilder type(SqlCommandType type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the mapper identifier (required).
     * Must be in format "namespace.methodId".
     *
     * @param mapperId the mapper identifier
     * @return this builder
     */
    public SqlContextBuilder mapperId(String mapperId) {
      this.mapperId = mapperId;
      return this;
    }

    /**
     * Sets the execution parameters (optional).
     *
     * @param params the parameter map
     * @return this builder
     */
    public SqlContextBuilder params(Map<String, Object> params) {
      this.params = params;
      return this;
    }

    /**
     * Sets the datasource identifier (optional).
     *
     * @param datasource the datasource name
     * @return this builder
     */
    public SqlContextBuilder datasource(String datasource) {
      this.datasource = datasource;
      return this;
    }

    /**
     * Sets the MyBatis RowBounds for pagination (optional).
     *
     * @param rowBounds the RowBounds object
     * @return this builder
     */
    public SqlContextBuilder rowBounds(Object rowBounds) {
      this.rowBounds = rowBounds;
      return this;
    }

    /**
     * Builds the SqlContext instance with validation.
     *
     * @return the constructed SqlContext
     * @throws IllegalArgumentException if required fields are null or invalid
     */
    public SqlContext build() {
      // Validate required fields
      if (sql == null || sql.trim().isEmpty()) {
        throw new IllegalArgumentException("sql cannot be null or empty");
      }
      if (type == null) {
        throw new IllegalArgumentException("type cannot be null");
      }
      if (mapperId == null || mapperId.trim().isEmpty()) {
        throw new IllegalArgumentException("mapperId cannot be null or empty");
      }

      // Validate mapperId format (must contain at least one dot)
      if (!mapperId.contains(".")) {
        throw new IllegalArgumentException(
            "mapperId must be in format 'namespace.methodId', got: " + mapperId);
      }

      return new SqlContext(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SqlContext that = (SqlContext) o;
    // Use getStatement() to ensure both statement and parsedSql are considered
    return Objects.equals(sql, that.sql)
        && Objects.equals(getStatement(), that.getStatement())
        && type == that.type
        && Objects.equals(mapperId, that.mapperId)
        && Objects.equals(params, that.params)
        && Objects.equals(datasource, that.datasource)
        && Objects.equals(rowBounds, that.rowBounds);
  }

  @Override
  public int hashCode() {
    // Use getStatement() for consistent hashing
    return Objects.hash(sql, getStatement(), type, mapperId, params, datasource, rowBounds);
  }

  @Override
  public String toString() {
    return "SqlContext{"
        + "sql='" + sql + '\''
        + ", statement=" + getStatement()
        + ", type=" + type
        + ", mapperId='" + mapperId + '\''
        + ", params=" + params
        + ", datasource='" + datasource + '\''
        + ", rowBounds=" + rowBounds
        + '}';
  }
}
