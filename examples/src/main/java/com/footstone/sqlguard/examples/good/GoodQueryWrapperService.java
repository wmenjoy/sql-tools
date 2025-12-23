package com.footstone.sqlguard.examples.good;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * GOOD EXAMPLES: MyBatis-Plus QueryWrapper usage with safe patterns.
 *
 * <p>This class demonstrates corrected QueryWrapper conditions that pass
 * SQL safety validation.
 *
 * <p><strong>Fixes Applied:</strong>
 * <ul>
 *   <li>Added business keys (id, user_id) to QueryWrapper conditions</li>
 *   <li>Combined blacklist fields with high-cardinality fields</li>
 *   <li>Removed dummy conditions</li>
 *   <li>Used IPage for pagination</li>
 * </ul>
 *
 * <p><strong>Design Reference:</strong> Phase 2 (Tasks 2.2-2.12), Phase 3 (Task 3.4)
 */
public class GoodQueryWrapperService {

    /**
     * GOOD: QueryWrapper with primary key condition.
     * FIX: Added eq("id", userId) for single-row query
     */
    public void selectUserById(Long userId) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("id", userId);
        // mapper.selectOne(wrapper); // Single row
    }

    /**
     * GOOD: QueryWrapper with business key + blacklist field.
     * FIX: Combined user_id (high cardinality) with deleted (blacklist)
     */
    public void selectActiveUserById(Long userId) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("id", userId)
               .eq("deleted", 0);
        // mapper.selectOne(wrapper); // Proper selectivity
    }

    /**
     * GOOD: QueryWrapper with multiple business keys.
     * FIX: Added email (unique) and status for filtering
     */
    public void selectUserByEmail(String email) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email)
               .eq("status", "active");
        // mapper.selectOne(wrapper); // Unique key query
    }

    /**
     * GOOD: QueryWrapper with pagination (IPage).
     * FIX: Used IPage<T> for bounded result set
     */
    public void selectUsersWithPagination(Long userId) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .eq("deleted", 0)
               .orderByDesc("created_at");
        
        IPage<Object> page = new Page<>(1, 20); // Page 1, 20 rows per page
        // mapper.selectPage(page, wrapper); // Paginated query
    }

    /**
     * GOOD: QueryWrapper with business key range.
     * FIX: Added date range condition for temporal filtering
     */
    public void selectOrdersByDateRange(Long userId, String startDate, String endDate) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .between("created_at", startDate, endDate)
               .orderByDesc("created_at");
        
        IPage<Object> page = new Page<>(1, 50);
        // mapper.selectPage(page, wrapper); // Filtered and paginated
    }

    /**
     * GOOD: UpdateWrapper with primary key condition.
     * FIX: Added eq("id", userId) for targeted update
     */
    public void updateUserStatus(Long userId, String status) {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("status", status)
               .eq("id", userId);
        // mapper.update(null, wrapper); // Single row update
    }

    /**
     * GOOD: UpdateWrapper with composite key.
     * FIX: Combined user_id and order_id for specific update
     */
    public void updateOrderStatus(Long userId, Long orderId, String status) {
        UpdateWrapper<Object> wrapper = new UpdateWrapper<>();
        wrapper.set("status", status)
               .eq("user_id", userId)
               .eq("order_id", orderId);
        // mapper.update(null, wrapper); // Targeted update
    }

    /**
     * GOOD: QueryWrapper with IN clause (business keys).
     * FIX: Used IN with user_id list for batch query
     */
    public void selectUsersByIds(java.util.List<Long> userIds) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.in("id", userIds)
               .eq("deleted", 0)
               .orderByAsc("id");
        
        IPage<Object> page = new Page<>(1, 100);
        // mapper.selectPage(page, wrapper); // Batch query with pagination
    }

    /**
     * GOOD: QueryWrapper with LIKE and business key.
     * FIX: Combined LIKE with pagination for search
     */
    public void searchUsersByName(String namePattern) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.like("name", namePattern)
               .eq("deleted", 0)
               .orderByAsc("name");
        
        IPage<Object> page = new Page<>(1, 20);
        // mapper.selectPage(page, wrapper); // Search with pagination
    }

    /**
     * GOOD: QueryWrapper with nested conditions (business keys).
     * FIX: Used business keys in nested conditions
     */
    public void selectUsersComplexConditions(Long userId, String status) {
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .nested(w -> w.eq("status", status).or().eq("status", "pending"))
               .eq("deleted", 0)
               .orderByDesc("created_at");
        
        IPage<Object> page = new Page<>(1, 30);
        // mapper.selectPage(page, wrapper); // Complex query with pagination
    }
}













