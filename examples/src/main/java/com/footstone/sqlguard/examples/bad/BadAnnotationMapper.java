package com.footstone.sqlguard.examples.bad;

import org.apache.ibatis.annotations.*;

/**
 * BAD EXAMPLES: Annotation-based MyBatis mappers with dangerous SQL patterns.
 *
 * <p>This class demonstrates all violation types using @Select, @Update, @Delete annotations.
 * These patterns are detected by the SQL Scanner during static analysis.
 *
 * <p><strong>Violations Demonstrated:</strong>
 * <ul>
 *   <li>NoWhereClause: DELETE/UPDATE without WHERE</li>
 *   <li>DummyCondition: WHERE 1=1, WHERE true</li>
 *   <li>BlacklistOnly: WHERE deleted=0 only</li>
 *   <li>NoPagination: SELECT without LIMIT</li>
 *   <li>MissingOrderBy: LIMIT without ORDER BY</li>
 * </ul>
 *
 * <p><strong>Design Reference:</strong> Phase 2 (Tasks 2.2-2.12), Phase 3 (Task 3.3)
 */
@Mapper
public interface BadAnnotationMapper {

    /**
     * BAD: DELETE without WHERE clause.
     * VIOLATION: NoWhereClause (CRITICAL)
     * IMPACT: Deletes ALL users from database
     */
    @Delete("DELETE FROM users")
    void deleteAllUsers();

    /**
     * BAD: UPDATE without WHERE clause.
     * VIOLATION: NoWhereClause (CRITICAL)
     * IMPACT: Updates ALL users to inactive status
     */
    @Update("UPDATE users SET status = 'inactive'")
    void deactivateAllUsers();

    /**
     * BAD: SELECT without WHERE clause and no pagination.
     * VIOLATION: NoWhereClause (CRITICAL), NoPagination (CRITICAL)
     * IMPACT: Loads entire users table into memory
     */
    @Select("SELECT * FROM users")
    java.util.List<java.util.Map<String, Object>> selectAllUsers();

    /**
     * BAD: SELECT with dummy condition WHERE 1=1.
     * VIOLATION: DummyCondition (HIGH)
     * IMPACT: Appears to filter but causes full table scan
     */
    @Select("SELECT * FROM users WHERE 1=1")
    java.util.List<java.util.Map<String, Object>> selectUsersWithDummy();

    /**
     * BAD: SELECT with dummy condition WHERE true.
     * VIOLATION: DummyCondition (HIGH)
     * IMPACT: Boolean literal provides no filtering
     */
    @Select("SELECT * FROM users WHERE true")
    java.util.List<java.util.Map<String, Object>> selectUsersWithTrue();

    /**
     * BAD: SELECT with blacklist-only WHERE condition.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: WHERE deleted=0 matches 99%+ of rows (near-full-table scan)
     */
    @Select("SELECT * FROM users WHERE deleted = 0")
    java.util.List<java.util.Map<String, Object>> selectActiveUsers();

    /**
     * BAD: SELECT with multiple blacklist fields only.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: Low-cardinality fields provide minimal selectivity
     */
    @Select("SELECT * FROM users WHERE deleted = 0 AND status = 'active'")
    java.util.List<java.util.Map<String, Object>> selectActiveEnabledUsers();

    /**
     * BAD: SELECT without pagination.
     * VIOLATION: NoPagination (HIGH - blacklist-only WHERE)
     * IMPACT: Returns unbounded result set
     */
    @Select("SELECT * FROM orders WHERE deleted = 0")
    java.util.List<java.util.Map<String, Object>> selectAllOrders();

    /**
     * BAD: SELECT with LIMIT but no ORDER BY.
     * VIOLATION: MissingOrderBy (MEDIUM)
     * IMPACT: Non-deterministic pagination results
     */
    @Select("SELECT * FROM users WHERE status = 'active' LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUsersNoOrderBy();

    /**
     * BAD: SELECT with LIMIT but no WHERE condition.
     * VIOLATION: NoConditionPagination (HIGH)
     * IMPACT: Paginates entire table without filtering
     */
    @Select("SELECT * FROM users LIMIT 10")
    java.util.List<java.util.Map<String, Object>> selectUsersLimitNoWhere();

    /**
     * BAD: SELECT with deep pagination (large OFFSET).
     * VIOLATION: DeepPagination (HIGH)
     * IMPACT: Database scans and discards first 50,000 rows
     */
    @Select("SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 20 OFFSET 50000")
    java.util.List<java.util.Map<String, Object>> selectUsersDeepOffset();

    /**
     * BAD: SELECT with large page size.
     * VIOLATION: LargePageSize (MEDIUM)
     * IMPACT: Memory pressure from loading 5000 rows
     */
    @Select("SELECT * FROM users WHERE status = 'active' LIMIT 5000")
    java.util.List<java.util.Map<String, Object>> selectUsersLargeLimit();

    /**
     * BAD: UPDATE with dummy condition.
     * VIOLATION: DummyCondition (HIGH)
     * IMPACT: Updates all users (dummy condition provides no filtering)
     */
    @Update("UPDATE users SET last_login = NOW() WHERE 1=1")
    void updateUsersWithDummy();

    /**
     * BAD: DELETE with blacklist-only WHERE.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: Deletes most users (status='inactive' is low cardinality)
     */
    @Delete("DELETE FROM users WHERE status = 'inactive'")
    void deleteInactiveUsers();

    /**
     * BAD: Complex query with multiple violations.
     * VIOLATION: DummyCondition (HIGH), BlacklistFields (HIGH), NoPagination (HIGH)
     * IMPACT: Compound violations create severe performance issues
     */
    @Select("SELECT * FROM users WHERE 1=1 AND deleted = 0")
    java.util.List<java.util.Map<String, Object>> selectMultipleViolations();
}
