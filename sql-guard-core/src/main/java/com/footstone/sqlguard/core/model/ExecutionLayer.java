package com.footstone.sqlguard.core.model;

/**
 * Represents the execution layer/technology stack that executed the SQL statement.
 *
 * <p>This enum provides a framework-agnostic way to identify the persistence technology
 * used, allowing the audit system to work uniformly across MyBatis, JDBC, JPA, and other
 * persistence frameworks.</p>
 *
 * <p><strong>Usage in Audit Context:</strong></p>
 * <ul>
 *   <li>{@code MYBATIS} - SQL executed through MyBatis or MyBatis-Plus</li>
 *   <li>{@code JDBC} - SQL executed through plain JDBC (Druid, HikariCP, P6Spy, etc.)</li>
 *   <li>{@code JPA} - SQL executed through JPA/Hibernate (future support)</li>
 *   <li>{@code SPRING_DATA} - SQL executed through Spring Data JDBC (future support)</li>
 *   <li>{@code UNKNOWN} - Cannot determine the execution layer</li>
 * </ul>
 *
 * @since 2.1.0
 */
public enum ExecutionLayer {
    /**
     * MyBatis or MyBatis-Plus framework.
     * <p>In this layer, statementId typically contains the full qualified mapper method name
     * (e.g., "com.example.UserMapper.selectById").</p>
     */
    MYBATIS,

    /**
     * Plain JDBC execution (via Druid, HikariCP, P6Spy, etc.).
     * <p>In this layer, statementId may be null or derived from stack traces if enabled.</p>
     */
    JDBC,

    /**
     * JPA/Hibernate framework (future support).
     * <p>Reserved for future implementation when JPA integration is added.</p>
     */
    JPA,

    /**
     * Spring Data JDBC framework (future support).
     * <p>Reserved for future implementation when Spring Data JDBC integration is added.</p>
     */
    SPRING_DATA,

    /**
     * Unknown or unidentifiable execution layer.
     * <p>Used as a fallback when the execution layer cannot be determined.</p>
     */
    UNKNOWN
}
