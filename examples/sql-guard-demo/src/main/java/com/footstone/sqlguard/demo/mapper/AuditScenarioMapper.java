package com.footstone.sqlguard.demo.mapper;

import com.footstone.sqlguard.demo.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for audit scenario demonstrations.
 *
 * <p>This mapper provides SQL operations specifically designed to trigger
 * various audit scenarios for the SQL Audit Platform demonstration.</p>
 *
 * <p><strong>Scenarios Demonstrated:</strong></p>
 * <ul>
 *   <li>Slow Query (5 second delay) - SlowQueryChecker</li>
 *   <li>Missing WHERE Update - ActualImpactNoWhereChecker</li>
 *   <li>Deep Pagination (offset 10000) - DeepPaginationChecker</li>
 *   <li>Error SQL - ErrorRateChecker</li>
 *   <li>Large Result Set - NoPaginationChecker</li>
 * </ul>
 *
 * @see com.footstone.sqlguard.demo.controller.AuditScenarioController
 */
public interface AuditScenarioMapper {

    /**
     * Scenario 1: Slow Query (5 second delay).
     *
     * <p>This query uses MySQL SLEEP function to simulate a slow query.
     * The SlowQueryChecker should detect this and generate a HIGH severity audit log.</p>
     *
     * @return list of users after the delay
     */
    @Select("SELECT * FROM user WHERE id > 0 AND SLEEP(5) = 0 LIMIT 10")
    List<User> slowQuery();

    /**
     * Scenario 2: Missing WHERE Update.
     *
     * <p>This update affects all rows in the table without a WHERE clause.
     * The ActualImpactNoWhereChecker should detect this and generate a CRITICAL severity audit log.</p>
     *
     * @return number of rows affected
     */
    @Update("UPDATE user SET status = 'INACTIVE'")
    int updateWithoutWhere();

    /**
     * Scenario 3: Deep Pagination (offset 10000).
     *
     * <p>This query uses a high offset value which is inefficient for large tables.
     * The DeepPaginationChecker should detect this and generate a MEDIUM severity audit log.</p>
     *
     * @return list of users starting from offset 10000
     */
    @Select("SELECT * FROM user ORDER BY id LIMIT 100 OFFSET 10000")
    List<User> deepPagination();

    /**
     * Scenario 4: Error SQL (syntax error).
     *
     * <p>This query has a syntax error to trigger SQL execution failure.
     * The ErrorRateChecker should aggregate errors and detect error rate spikes.</p>
     *
     * @return nothing (will throw exception)
     */
    @Select("SELECT * FROM non_existent_table WHERE 1=1")
    List<User> invalidSql();

    /**
     * Scenario 5: Large Page Size.
     *
     * <p>This query requests too many rows at once.
     * The LargePageSizeChecker should detect this and generate a MEDIUM severity audit log.</p>
     *
     * @param limit the number of rows to retrieve (should be > 1000 to trigger)
     * @return list of users
     */
    @Select("SELECT * FROM user WHERE status = 'ACTIVE' ORDER BY id LIMIT #{limit}")
    List<User> largePageSize(@Param("limit") int limit);

    /**
     * Scenario 6: Query without pagination.
     *
     * <p>This query selects all rows without LIMIT clause.
     * The NoPaginationChecker should detect this and generate a MEDIUM/CRITICAL severity audit log.</p>
     *
     * @return list of all users
     */
    @Select("SELECT * FROM user WHERE status = 'ACTIVE'")
    List<User> selectAllWithoutPagination();

    /**
     * Fast query for normal load simulation.
     *
     * <p>This is a simple query that should execute quickly for load generator baseline.</p>
     *
     * @param id user ID
     * @return user if found
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    /**
     * Query with proper pagination for comparison.
     *
     * <p>This demonstrates a properly paginated query as a baseline.</p>
     *
     * @param offset start offset
     * @param limit number of rows
     * @return list of users
     */
    @Select("SELECT * FROM user WHERE status = 'ACTIVE' ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<User> selectWithProperPagination(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * Scenario 7: Missing ORDER BY (pagination without ORDER BY).
     *
     * <p>This query uses LIMIT but has no ORDER BY clause, causing unstable result ordering.
     * The MissingOrderByChecker should detect this and generate a LOW severity audit log.</p>
     *
     * @return list of users with unstable ordering
     */
    @Select("SELECT * FROM user WHERE id > 0 LIMIT 20")
    List<User> paginationWithoutOrderBy();

    /**
     * Scenario 8: No condition pagination (pagination without WHERE).
     *
     * <p>This query uses LIMIT but has no WHERE clause, potentially returning all rows.
     * The NoConditionPaginationChecker should detect this and generate a MEDIUM severity audit log.</p>
     *
     * @return list of users without filtering
     */
    @Select("SELECT * FROM user ORDER BY id LIMIT 50")
    List<User> paginationWithoutCondition();

    /**
     * Scenario 9: Blacklist field only (WHERE only uses blacklisted fields).
     *
     * <p>This query only uses blacklisted fields (status, deleted) in WHERE clause,
     * causing near-full-table scans. The BlacklistFieldChecker should detect this
     * and generate a HIGH severity audit log.</p>
     *
     * @return list of users matching blacklist-only condition
     */
    @Select("SELECT * FROM user WHERE status = 'ACTIVE' AND deleted = 0 LIMIT 10")
    List<User> selectWithBlacklistFieldOnly();

    /**
     * Scenario 10: Whitelist field violation (accessing non-whitelisted fields).
     *
     * <p>This query accesses all fields including status and deleted which might not be whitelisted.
     * The WhitelistFieldChecker should detect this and generate a HIGH severity audit log.</p>
     *
     * @return list of users with all fields
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectWithNonWhitelistFields(@Param("id") Long id);

    /**
     * Scenario 11: Dummy condition (WHERE with dummy conditions like 1=1).
     *
     * <p>This query uses dummy condition "1=1" which makes WHERE clause useless.
     * The DummyConditionChecker should detect this and generate a HIGH severity audit log.</p>
     *
     * @return list of all users due to dummy condition
     */
    @Select("SELECT * FROM user WHERE 1=1 AND id > 0 LIMIT 10")
    List<User> selectWithDummyCondition();

    /**
     * Scenario 12: No WHERE clause (SELECT without WHERE).
     *
     * <p>This query has no WHERE clause, potentially returning all rows.
     * The NoWhereClauseChecker should detect this and generate a HIGH severity audit log.</p>
     *
     * @return list of all users
     */
    @Select("SELECT id, username FROM user")
    List<User> selectWithoutWhere();

    /**
     * Scenario 13: DELETE without WHERE.
     *
     * <p>This DELETE has no WHERE clause, affecting all rows.
     * The NoWhereClauseChecker should detect this and generate a CRITICAL severity audit log.</p>
     *
     * @return number of rows deleted
     */
    @Update("DELETE FROM user WHERE status = 'INACTIVE'")
    int deleteWithoutProperWhere();
}





