package com.example;

import org.apache.ibatis.annotations.*;

public class OuterClass {
    public interface NestedClassMapper {
        @Select("SELECT * FROM nested WHERE id = #{id}")
        Object findById(Long id);
    }
}







