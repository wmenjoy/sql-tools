package com.footstone.sqlguard.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.footstone.sqlguard.demo.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for Order entity.
 *
 * <p>This mapper extends BaseMapper to demonstrate SQL Guard validation with
 * MyBatis-Plus CRUD operations and QueryWrapper usage.</p>
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    // MyBatis-Plus provides built-in CRUD methods
    // Additional custom methods can be added here
}
