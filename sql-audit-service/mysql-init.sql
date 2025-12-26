# ============================================================================
# MySQL初始化SQL脚本
# ============================================================================
# 此脚本会在MySQL首次启动时自动执行
# 创建sql_audit_service数据库和必要的用户权限
# ============================================================================

-- 创建数据库(如果不存在)
CREATE DATABASE IF NOT EXISTS `sql_audit_service`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE `sql_audit_service`;

-- 创建审计报告表
CREATE TABLE IF NOT EXISTS `audit_reports` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `report_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '报告唯一标识',
  `sql_id` VARCHAR(255) NOT NULL COMMENT 'SQL标识',
  `sql_text` TEXT NOT NULL COMMENT 'SQL语句',
  `sql_type` VARCHAR(32) COMMENT 'SQL类型(SELECT/INSERT/UPDATE/DELETE)',
  `mapper_id` VARCHAR(255) COMMENT 'MyBatis Mapper ID',
  `risk_level` VARCHAR(32) COMMENT '风险等级(LOW/MEDIUM/HIGH/CRITICAL)',
  `risk_score` INT COMMENT '风险评分',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  INDEX `idx_sql_id` (`sql_id`),
  INDEX `idx_risk_level` (`risk_level`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计报告表';

-- 创建检查器配置表
CREATE TABLE IF NOT EXISTS `checker_configs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `checker_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '检查器ID',
  `checker_name` VARCHAR(128) NOT NULL COMMENT '检查器名称',
  `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  `config_json` TEXT COMMENT '配置JSON',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  INDEX `idx_checker_id` (`checker_id`),
  INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检查器配置表';

-- 创建SQL执行日志表(时序数据)
CREATE TABLE IF NOT EXISTS `sql_executions` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `sql_id` VARCHAR(255) NOT NULL COMMENT 'SQL标识',
  `sql_text` TEXT NOT NULL COMMENT 'SQL语句',
  `execution_time_ms` BIGINT COMMENT '执行时间(毫秒)',
  `rows_affected` INT COMMENT '影响行数',
  `error_message` TEXT COMMENT '错误信息',
  `timestamp` TIMESTAMP NOT NULL COMMENT '执行时间戳',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  INDEX `idx_sql_id` (`sql_id`),
  INDEX `idx_timestamp` (`timestamp`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL执行日志表'
PARTITION BY RANGE (TO_DAYS(`timestamp`)) (
  PARTITION p_history VALUES LESS THAN (TO_DAYS('2024-01-01')),
  PARTITION p_2024_01 VALUES LESS THAN (TO_DAYS('2024-02-01')),
  PARTITION p_2024_02 VALUES LESS THAN (TO_DAYS('2024-03-01')),
  PARTITION p_2024_03 VALUES LESS THAN (TO_DAYS('2024-04-01')),
  PARTITION p_2024_04 VALUES LESS THAN (TO_DAYS('2024-05-01')),
  PARTITION p_2024_05 VALUES LESS THAN (TO_DAYS('2024-06-01')),
  PARTITION p_2024_06 VALUES LESS THAN (TO_DAYS('2024-07-01')),
  PARTITION p_2024_07 VALUES LESS THAN (TO_DAYS('2024-08-01')),
  PARTITION p_2024_08 VALUES LESS THAN (TO_DAYS('2024-09-01')),
  PARTITION p_2024_09 VALUES LESS THAN (TO_DAYS('2024-10-01')),
  PARTITION p_2024_10 VALUES LESS THAN (TO_DAYS('2024-11-01')),
  PARTITION p_2024_11 VALUES LESS THAN (TO_DAYS('2024-12-01')),
  PARTITION p_2024_12 VALUES LESS THAN (TO_DAYS('2025-01-01')),
  PARTITION p_2025_01 VALUES LESS THAN (TO_DAYS('2025-02-01')),
  PARTITION p_2025_02 VALUES LESS THAN (TO_DAYS('2025-03-01')),
  PARTITION p_2025_03 VALUES LESS THAN (TO_DAYS('2025-04-01')),
  PARTITION p_2025_04 VALUES LESS THAN (TO_DAYS('2025-05-01')),
  PARTITION p_2025_05 VALUES LESS THAN (TO_DAYS('2025-06-01')),
  PARTITION p_2025_06 VALUES LESS THAN (TO_DAYS('2025-07-01')),
  PARTITION p_2025_07 VALUES LESS THAN (TO_DAYS('2025-08-01')),
  PARTITION p_2025_08 VALUES LESS THAN (TO_DAYS('2025-09-01')),
  PARTITION p_2025_09 VALUES LESS THAN (TO_DAYS('2025-10-01')),
  PARTITION p_2025_10 VALUES LESS THAN (TO_DAYS('2025-11-01')),
  PARTITION p_2025_11 VALUES LESS THAN (TO_DAYS('2025-12-01')),
  PARTITION p_2025_12 VALUES LESS THAN (TO_DAYS('2026-01-01')),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 插入默认检查器配置
INSERT INTO `checker_configs` (`checker_id`, `checker_name`, `enabled`, `config_json`) VALUES
('no-where-clause', 'No Where Clause Checker', TRUE, '{"enabled": true, "skipMaster": false}'),
('blacklist-field', 'Blacklist Field Checker', TRUE, '{"enabled": true, "fields": ["password", "secret"]}'),
('whitelist-field', 'Whitelist Field Checker', FALSE, '{"enabled": false, "fields": []}'),
('no-pagination', 'No Pagination Checker', TRUE, '{"enabled": true, "maxRows": 1000}'),
('deep-pagination', 'Deep Pagination Checker', TRUE, '{"enabled": true, "maxOffset": 10000}'),
('large-page-size', 'Large Page Size Checker', TRUE, '{"enabled": true, "maxPageSize": 1000}'),
('missing-order-by', 'Missing Order By Checker', TRUE, '{"enabled": true}'),
('no-condition-pagination', 'No Condition Pagination Checker', TRUE, '{"enabled": true}'),
('logical-pagination', 'Logical Pagination Checker', TRUE, '{"enabled": true}'),
('dummy-condition', 'Dummy Condition Checker', TRUE, '{"enabled": true, "patterns": ["1=1", "0=0"]}')
ON DUPLICATE KEY UPDATE
  `updated_at` = CURRENT_TIMESTAMP;

-- 授权(如果需要创建专用用户)
-- CREATE USER IF NOT EXISTS 'sql_audit'@'%' IDENTIFIED BY 'sql_audit_password';
-- GRANT ALL PRIVILEGES ON sql_audit_service.* TO 'sql_audit'@'%';
-- FLUSH PRIVILEGES;
