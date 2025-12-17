package com.footstone.sqlguard.examples.bad;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * BAD EXAMPLES: MyBatis-Plus QueryWrapper usage with dangerous patterns.
 *
 * <p>This class demonstrates problematic QueryWrapper conditions that are detected
 * by the SQL Scanner's QueryWrapper scanner during static analysis.
 *
 * <p><strong>Violations Demonstrated:</strong>
 * <ul>
 *   <li>Empty QueryWrapper (no conditions)</li>
 *   <li>Blacklist-only conditions (deleted=0, status='active')</li>
 *   <li>Dummy conditions (1=1, true)</li>
 *   <li>No pagination with QueryWrapper</li>
 * </ul>
 *
 * <p><strong>Note:</strong> QueryWrapper violations are detected at runtime by interceptors
 * and during static analysis by QueryWrapperScanner.
 *
 * <p><strong>Design Reference:</strong> Phase 2 (Tasks 2.2-2.12), Phase 3 (Task 3.4)
 */
public class BadQueryWrapperService {

    /**
     * BAD: Empty QueryWrapper (no conditions).
     * VIOLATION: NoWhereClause equivalent
     * IMPACT: Selects ALL users from database
     */
    public void selectAllUsersEmptyWrapper() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        // mapper.selectList(wrapper); // Returns all rows
    }

    /**
     * BAD: QueryWrapper with blacklist-only condition.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: WHERE deleted=0 matches 99%+ of rows
     */
    public void selectActiveUsersBlacklistOnly() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        // mapper.selectList(wrapper); // Near-full-table scan
    }

    /**
     * BAD: QueryWrapper with multiple blacklist fields.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: Low-cardinality fields provide minimal filtering
     */
    public void selectEnabledUsersBlacklistOnly() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
               .eq("status", "active")
               .eq("enabled", 1);
        // mapper.selectList(wrapper); // Still near-full-table scan
    }

    /**
     * BAD: QueryWrapper with dummy condition (always true).
     * VIOLATION: DummyCondition (HIGH)
     * IMPACT: Condition provides no filtering
     */
    public void selectUsersWithDummyCondition() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.apply("1=1");
        // mapper.selectList(wrapper); // Full table scan
    }

    /**
     * BAD: QueryWrapper without pagination.
     * VIOLATION: NoPagination (HIGH)
     * IMPACT: Returns unbounded result set
     */
    public void selectAllOrdersNoPagination() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        // mapper.selectList(wrapper); // No LIMIT, loads all rows
    }

    /**
     * BAD: UpdateWrapper with empty conditions.
     * VIOLATION: NoWhereClause equivalent
     * IMPACT: Updates ALL users
     */
    public void updateAllUsersEmptyWrapper() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("status", "inactive");
        // mapper.update(null, wrapper); // Updates all rows
    }

    /**
     * BAD: UpdateWrapper with blacklist-only condition.
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: Updates most users (low selectivity)
     */
    public void updateUsersBlacklistOnly() {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("last_login", "NOW()")
               .eq("status", "active");
        // mapper.update(null, wrapper); // Updates many rows
    }

    /**
     * BAD: QueryWrapper with dummy condition and blacklist.
     * VIOLATION: DummyCondition (HIGH), BlacklistFields (HIGH)
     * IMPACT: Compound violations
     */
    public void selectMultipleViolations() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.apply("1=1")
               .eq("deleted", 0);
        // mapper.selectList(wrapper); // Multiple issues
    }

    /**
     * BAD: QueryWrapper with OR conditions (blacklist-only).
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: OR with low-cardinality fields matches most rows
     */
    public void selectUsersWithOrBlacklist() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active")
               .or()
               .eq("status", "pending");
        // mapper.selectList(wrapper); // Matches most rows
    }

    /**
     * BAD: QueryWrapper with nested conditions (blacklist-only).
     * VIOLATION: BlacklistFields (HIGH)
     * IMPACT: Complex conditions still use only blacklist fields
     */
    public void selectUsersNestedBlacklist() {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.nested(w -> w.eq("deleted", 0).eq("enabled", 1))
               .eq("status", "active");
        // mapper.selectList(wrapper); // All blacklist fields
    }
}
