package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

public class ContextTestService {
    // Field initializer
    private QueryWrapper<User> fieldWrapper = new QueryWrapper<>();

    // Static block
    static {
        QueryWrapper<Config> staticWrapper = new QueryWrapper<>();
    }

    // Constructor
    public ContextTestService() {
        QueryWrapper<User> constructorWrapper = new QueryWrapper<>();
    }

    // Method
    public void findUsers() {
        QueryWrapper<User> methodWrapper = new QueryWrapper<>();
    }

    // Lambda expression
    public void processUsers() {
        users.forEach(u -> {
            QueryWrapper<User> lambdaWrapper = new QueryWrapper<>();
        });
    }
}


















