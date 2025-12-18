package com.footstone.sqlguard.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.footstone.sqlguard.demo.entity.Order;
import com.footstone.sqlguard.demo.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Order service demonstrating MyBatis-Plus QueryWrapper usage.
 *
 * <p>This service includes both safe and unsafe QueryWrapper patterns for
 * SQL Guard validation demonstration.</p>
 */
@Service
public class OrderService {

    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * SAFE: Query with proper WHERE condition using high-selectivity field.
     */
    public Order findById(Long id) {
        return orderMapper.selectById(id);
    }

    /**
     * SAFE: Query with WHERE condition.
     */
    public List<Order> findByUserId(Long userId) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return orderMapper.selectList(wrapper);
    }

    /**
     * UNSAFE: Query without WHERE condition.
     * Triggers: NoPaginationChecker violation
     */
    public List<Order> findAllUnsafe() {
        return orderMapper.selectList(null);
    }

    /**
     * UNSAFE: Query with only blacklist field (status).
     * Triggers: BlacklistFieldChecker HIGH violation
     */
    public List<Order> findByStatusOnlyUnsafe(String status) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        return orderMapper.selectList(wrapper);
    }

    /**
     * UNSAFE: Update without WHERE condition.
     * Triggers: NoWhereClauseChecker CRITICAL violation
     */
    public int updateAllStatusUnsafe(String status) {
        Order order = new Order();
        order.setStatus(status);
        return orderMapper.update(order, null);
    }

    /**
     * UNSAFE: Delete without WHERE condition.
     * Triggers: NoWhereClauseChecker CRITICAL violation
     */
    public int deleteAllUnsafe() {
        return orderMapper.delete(null);
    }

    /**
     * SAFE: Update with proper WHERE condition.
     */
    public int updateStatus(Long id, String status) {
        Order order = new Order();
        order.setStatus(status);
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        return orderMapper.update(order, wrapper);
    }

    /**
     * SAFE: Delete with proper WHERE condition.
     */
    public int deleteById(Long id) {
        return orderMapper.deleteById(id);
    }
}



