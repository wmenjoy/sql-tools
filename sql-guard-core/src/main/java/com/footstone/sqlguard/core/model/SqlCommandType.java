package com.footstone.sqlguard.core.model;

/**
 * SQL command type classification for categorizing SQL statements.
 *
 * <p>SqlCommandType categorizes SQL statements into four main DML (Data Manipulation Language)
 * operations. This classification is used by validation checkers to apply type-specific rules.</p>
 *
 * <p><strong>Command Types:</strong></p>
 * <ul>
 *   <li><strong>SELECT</strong> - Data query operations</li>
 *   <li><strong>UPDATE</strong> - Data modification operations</li>
 *   <li><strong>DELETE</strong> - Data deletion operations</li>
 *   <li><strong>INSERT</strong> - Data insertion operations</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SqlCommandType type = SqlCommandType.fromString("SELECT");
 * if (type == SqlCommandType.SELECT) {
 *   // Apply SELECT-specific validation rules
 * }
 * }</pre>
 *
 * @see SqlContext
 */
public enum SqlCommandType {
  /**
   * SELECT query - reads data from database.
   */
  SELECT,

  /**
   * UPDATE statement - modifies existing data.
   */
  UPDATE,

  /**
   * DELETE statement - removes data from database.
   */
  DELETE,

  /**
   * INSERT statement - adds new data to database.
   */
  INSERT,

  /**
   * UNKNOWN statement - unrecognized SQL command type.
   */
  UNKNOWN;

  /**
   * Converts a string to SqlCommandType enum (case-insensitive).
   *
   * <p>This method performs case-insensitive lookup and trims whitespace.
   * Returns null if the string doesn't match any known command type.</p>
   *
   * @param type the SQL command type string (e.g., "SELECT", "select", " UPDATE ")
   * @return the corresponding SqlCommandType, or null if not found
   */
  public static SqlCommandType fromString(String type) {
    if (type == null || type.trim().isEmpty()) {
      return null;
    }

    String normalized = type.trim().toUpperCase();
    try {
      return SqlCommandType.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
