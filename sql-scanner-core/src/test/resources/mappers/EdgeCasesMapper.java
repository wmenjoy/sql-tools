package com.example;

import org.apache.ibatis.annotations.*;

public interface EdgeCasesMapper {
    // Empty annotation - should be skipped with warning
    @Select("")
    User emptySelect();

    // Escaped quotes in SQL
    @Select("SELECT * FROM user WHERE name='O\\'Brien'")
    User findByName(String name);

    // Unicode characters in SQL
    @Select("SELECT * FROM user WHERE name = '张三'")
    User findChinese();

    // Complex SQL with multiple conditions
    @Select("SELECT u.*, r.role_name FROM user u LEFT JOIN role r ON u.role_id = r.id WHERE u.status = 'active' AND u.age > 18")
    List<User> findActiveAdults();
}







