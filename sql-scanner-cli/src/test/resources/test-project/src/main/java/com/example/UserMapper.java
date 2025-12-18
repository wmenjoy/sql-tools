package com.example;

import org.apache.ibatis.annotations.Select;

public interface UserMapper {
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectUserById(Long id);

    @Select("SELECT * FROM users WHERE name = #{name}")
    User selectUserByName(String name);
}







