package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * UserMapper for integration testing.
 * 
 * <p>Extends MyBatis-Plus BaseMapper which provides built-in CRUD methods:</p>
 * <ul>
 *   <li>selectById(Serializable id)</li>
 *   <li>selectList(Wrapper<T> queryWrapper)</li>
 *   <li>selectPage(IPage<T> page, Wrapper<T> queryWrapper)</li>
 *   <li>insert(T entity)</li>
 *   <li>updateById(T entity)</li>
 *   <li>deleteById(Serializable id)</li>
 * </ul>
 */
public interface UserMapper extends BaseMapper<User> {
  // BaseMapper provides all CRUD methods
  // No additional methods needed for basic testing
}









