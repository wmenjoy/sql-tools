package com.footstone.sqlguard.demo.mapper;

import com.footstone.sqlguard.demo.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis XML-based mapper for User entity.
 *
 * <p>This mapper uses XML configuration to demonstrate SQL Guard validation with
 * traditional MyBatis XML mappers. All unsafe methods are intentionally dangerous
 * for demo purposes.</p>
 */
public interface UserMapper {

    /**
     * SAFE: Find user by ID (high-selectivity field).
     */
    User findById(@Param("id") Long id);

    /**
     * SAFE: Find user by username.
     */
    User findByUsername(@Param("username") String username);

    /**
     * UNSAFE: Find all users without WHERE clause.
     * Triggers: NoPaginationChecker violation (MEDIUM/CRITICAL depending on table size)
     */
    List<User> findAllUnsafe();

    /**
     * UNSAFE: Delete all users without WHERE clause.
     * Triggers: NoWhereClauseChecker CRITICAL violation
     */
    int deleteAllUnsafe();

    /**
     * UNSAFE: Update all users without WHERE clause.
     * Triggers: NoWhereClauseChecker CRITICAL violation
     */
    int updateAllStatusUnsafe(@Param("status") String status);

    /**
     * UNSAFE: Query with dummy condition WHERE 1=1.
     * Triggers: DummyConditionChecker HIGH violation
     */
    List<User> findWithDummyCondition();

    /**
     * UNSAFE: Query with only blacklist field (status).
     * Triggers: BlacklistFieldChecker HIGH violation
     */
    List<User> findByStatusOnly(@Param("status") String status);

    /**
     * UNSAFE: Query with only blacklist field (deleted).
     * Triggers: BlacklistFieldChecker HIGH violation
     */
    List<User> findByDeletedOnly(@Param("deleted") Integer deleted);

    /**
     * UNSAFE: LIMIT without WHERE clause.
     * Triggers: NoConditionPaginationChecker CRITICAL violation
     */
    List<User> findWithLimitNoWhere(@Param("limit") int limit);

    /**
     * UNSAFE: Deep pagination with high offset.
     * Triggers: DeepPaginationChecker MEDIUM violation
     */
    List<User> findWithDeepOffset(@Param("pattern") String pattern, 
                                   @Param("limit") int limit, 
                                   @Param("offset") int offset);

    /**
     * UNSAFE: Large page size.
     * Triggers: LargePageSizeChecker MEDIUM violation
     */
    List<User> findWithLargePageSize(@Param("pattern") String pattern, 
                                      @Param("limit") int limit);

    /**
     * UNSAFE: Pagination without ORDER BY.
     * Triggers: MissingOrderByChecker LOW violation
     */
    List<User> findWithoutOrderBy(@Param("pattern") String pattern, 
                                   @Param("limit") int limit);

    /**
     * SAFE: Proper pagination with WHERE and ORDER BY.
     */
    List<User> findWithProperPagination(@Param("pattern") String pattern, 
                                         @Param("limit") int limit, 
                                         @Param("offset") int offset);
}














