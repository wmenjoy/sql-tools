package com.footstone.sqlguard.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User entity for demo application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    /**
     * Status field - intentionally low-cardinality for blacklist demo.
     * Typical values: ACTIVE, INACTIVE, SUSPENDED
     */
    private String status;

    /**
     * Soft delete flag - intentionally low-cardinality for blacklist demo.
     * Values: 0 (not deleted), 1 (deleted)
     */
    private Integer deleted;

    private LocalDateTime createTime;
}















