package com.example;

import org.apache.ibatis.annotations.*;

public interface MultiLineMapper {
    @Select({
        "SELECT * FROM user",
        "WHERE name = #{name}",
        "ORDER BY id"
    })
    List<User> findByName(String name);

    @Update(value = "UPDATE user SET name = #{name}", timeout = 30)
    int updateUserName(String name, Long id);
}







