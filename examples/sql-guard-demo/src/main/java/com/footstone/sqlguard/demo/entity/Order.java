package com.footstone.sqlguard.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order entity for demo application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime orderTime;
}








