package com.footstone.sqlguard.demo.mapper;

import com.footstone.sqlguard.demo.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MyBatis annotation-based mapper for User entity.
 *
 * <p>This mapper uses @Select/@Update/@Delete annotations to demonstrate SQL Guard
 * validation with annotation-based MyBatis mappers. All unsafe methods are intentionally
 * dangerous for demo purposes.</p>
 */
@Mapper
public interface UserAnnotationMapper {

    /**
     * SAFE: Find user by email with WHERE clause.
     */
    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    /**
     * UNSAFE: Count all users without WHERE clause.
     * Triggers: NoWhereClauseChecker CRITICAL violation (SELECT without WHERE)
     */
    @Select("SELECT COUNT(*) FROM user")
    long countAllUnsafe();

    /**
     * UNSAFE: Update with dummy condition WHERE 'a'='a'.
     * Triggers: DummyConditionChecker HIGH violation
     */
    @Update("UPDATE user SET status = #{status} WHERE 'a'='a'")
    int updateWithDummyCondition(@Param("status") String status);

    /**
     * UNSAFE: Delete with only blacklist field.
     * Triggers: BlacklistFieldChecker HIGH violation
     */
    @Delete("DELETE FROM user WHERE deleted = #{deleted}")
    int deleteByDeletedFlag(@Param("deleted") Integer deleted);

    /**
     * SAFE: Update with proper WHERE clause using high-selectivity field.
     */
    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * SAFE: Delete with proper WHERE clause using high-selectivity field.
     */
    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * UNSAFE: SELECT with LIMIT but no WHERE.
     * Triggers: NoConditionPaginationChecker CRITICAL violation
     */
    @Select("SELECT * FROM user LIMIT #{limit}")
    List<User> findTopNUnsafe(@Param("limit") int limit);
}








