package com.footstone.sqlguard.examples.good;

import org.apache.ibatis.annotations.*;

/**
 * GOOD EXAMPLES: Annotation-based MyBatis mappers with safe SQL patterns.
 *
 * <p>This class demonstrates corrected versions of dangerous SQL patterns.
 * All queries follow best practices and pass SQL safety validation.
 *
 * <p><strong>Fixes Applied:</strong>
 * <ul>
 *   <li>Added WHERE clauses to all DELETE/UPDATE/SELECT</li>
 *   <li>Removed dummy conditions (1=1, true)</li>
 *   <li>Combined blacklist fields with business keys</li>
 *   <li>Added LIMIT for pagination</li>
 *   <li>Added ORDER BY for deterministic results</li>
 * </ul>
 *
 * <p><strong>Design Reference:</strong> Phase 2 (Tasks 2.2-2.12), Phase 3 (Task 3.3)
 */
@Mapper
public interface GoodAnnotationMapper {

    /**
     * GOOD: DELETE with WHERE clause (single row).
     * FIX: Added WHERE id = #{id} to restrict deletion
     */
    @Delete("DELETE FROM users WHERE id = #{id}")
    void deleteUserById(@Param("id") Long id);

    /**
     * GOOD: UPDATE with WHERE clause (targeted update).
     * FIX: Added WHERE user_id = #{userId} for specific user
     */
    @Update("UPDATE users SET status = 'inactive' WHERE user_id = #{userId}")
    void deactivateUser(@Param("userId") Long userId);

    /**
     * GOOD: SELECT with WHERE and LIMIT (bounded result set).
     * FIX: Added WHERE status = #{status} and LIMIT 50
     */
    @Select("SELECT * FROM users WHERE status = #{status} ORDER BY id LIMIT 50")
    java.util.List<java.util.Map<String, Object>> selectUsersByStatus(@Param("status") String status);

    /**
     * GOOD: SELECT with proper WHERE condition (no dummy).
     * FIX: Replaced "WHERE 1=1" with meaningful condition
     */
    @Select("SELECT * FROM users WHERE name = #{name} ORDER BY id LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUsersByName(@Param("name") String name);

    /**
     * GOOD: SELECT with business key + blacklist field.
     * FIX: Added id (high cardinality) to WHERE clause
     */
    @Select("SELECT * FROM users WHERE id = #{id} AND deleted = 0")
    java.util.Map<String, Object> selectActiveUserById(@Param("id") Long id);

    /**
     * GOOD: SELECT with primary key and blacklist fields.
     * FIX: Combined user_id with status for proper selectivity
     */
    @Select("SELECT * FROM users WHERE user_id = #{userId} AND deleted = 0 AND status = 'active'")
    java.util.Map<String, Object> selectActiveUser(@Param("userId") Long userId);

    /**
     * GOOD: SELECT with WHERE and pagination.
     * FIX: Added user_id for filtering and LIMIT for pagination
     */
    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUserOrders(@Param("userId") Long userId);

    /**
     * GOOD: SELECT with LIMIT and ORDER BY (deterministic).
     * FIX: Added ORDER BY id for consistent pagination
     */
    @Select("SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUsersWithOrderBy();

    /**
     * GOOD: SELECT with WHERE and LIMIT (no full-table pagination).
     * FIX: Added WHERE status = 'active' for filtering
     */
    @Select("SELECT * FROM users WHERE status = 'active' ORDER BY created_at DESC LIMIT 10")
    java.util.List<java.util.Map<String, Object>> selectActiveUsersWithLimit();

    /**
     * GOOD: Cursor-based pagination (no deep OFFSET).
     * FIX: Replaced OFFSET with cursor (WHERE id > #{lastId})
     */
    @Select("SELECT * FROM users WHERE status = 'active' AND id > #{lastId} ORDER BY id LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUsersAfterCursor(@Param("lastId") Long lastId);

    /**
     * GOOD: SELECT with reasonable page size.
     * FIX: Reduced LIMIT from 5000 to 50 rows
     */
    @Select("SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 50")
    java.util.List<java.util.Map<String, Object>> selectUsersReasonableLimit();

    /**
     * GOOD: UPDATE with proper WHERE condition.
     * FIX: Replaced "WHERE 1=1" with WHERE id = #{id}
     */
    @Update("UPDATE users SET last_login = NOW() WHERE id = #{id}")
    void updateUserLastLogin(@Param("id") Long id);

    /**
     * GOOD: DELETE with primary key WHERE.
     * FIX: Added WHERE id = #{id} for single-row deletion
     */
    @Delete("DELETE FROM users WHERE id = #{id}")
    void deleteUserById2(@Param("id") Long id);

    /**
     * GOOD: Complex query with all fixes applied.
     * FIX: Added business key, removed dummy, added pagination and ORDER BY
     */
    @Select("SELECT * FROM users WHERE email = #{email} AND deleted = 0 ORDER BY id LIMIT 20")
    java.util.List<java.util.Map<String, Object>> selectUsersByEmail(@Param("email") String email);
}







