package com.example;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class UserService {
    public void findUsers() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        wrapper.like("name", "test");
    }
}




