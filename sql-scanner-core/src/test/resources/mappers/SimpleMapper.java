package com.example;

import org.apache.ibatis.annotations.*;

public interface SimpleMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    User getUserById(Long id);

    @Update("UPDATE user SET name = #{name} WHERE id = #{id}")
    int updateUser(User user);
}






