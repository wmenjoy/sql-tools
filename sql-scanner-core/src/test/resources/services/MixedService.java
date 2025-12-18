package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class MixedService {
    public void useMyBatisPlusWrapper() {
        QueryWrapper<User> wrapper = new QueryWrapper<>(); // SHOULD detect
    }

    public void useCustomWrapper() {
        // Note: Without actual custom.QueryWrapper class, this would be a compile error
        // This file demonstrates the concept for package verification
        com.example.custom.QueryWrapper wrapper = new com.example.custom.QueryWrapper(); // Should NOT detect
    }
}







