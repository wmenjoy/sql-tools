package com.example;

import org.apache.ibatis.annotations.*;

public interface ComplexMapper {
    @Select("SELECT * FROM user WHERE id = #{id}")
    @ResultMap("userResultMap")
    User getUserById(@Param("id") Long id);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteUser(Long id);

    @Insert("INSERT INTO user (name, email) VALUES (#{name}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(User user);
}







