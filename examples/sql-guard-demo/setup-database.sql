-- ===================================================
-- SQL Guard Demo - Database Setup Script
-- ===================================================
-- This script creates the database and initializes tables

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS sqlguard_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Use the database
USE sqlguard_demo;

-- Drop existing tables if they exist
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS user;

-- User table
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Order table
CREATE TABLE `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_order_time (order_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Product table
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL,
    INDEX idx_name (name),
    INDEX idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert 100 test users
INSERT INTO user (username, email, status, deleted) VALUES
('user001', 'user001@example.com', 'ACTIVE', 0),
('user002', 'user002@example.com', 'ACTIVE', 0),
('user003', 'user003@example.com', 'INACTIVE', 0),
('user004', 'user004@example.com', 'ACTIVE', 0),
('user005', 'user005@example.com', 'ACTIVE', 0),
('user006', 'user006@example.com', 'SUSPENDED', 0),
('user007', 'user007@example.com', 'ACTIVE', 0),
('user008', 'user008@example.com', 'ACTIVE', 0),
('user009', 'user009@example.com', 'ACTIVE', 1),
('user010', 'user010@example.com', 'ACTIVE', 0),
('user011', 'user011@example.com', 'ACTIVE', 0),
('user012', 'user012@example.com', 'INACTIVE', 0),
('user013', 'user013@example.com', 'ACTIVE', 0),
('user014', 'user014@example.com', 'ACTIVE', 0),
('user015', 'user015@example.com', 'ACTIVE', 0),
('user016', 'user016@example.com', 'ACTIVE', 0),
('user017', 'user017@example.com', 'ACTIVE', 0),
('user018', 'user018@example.com', 'SUSPENDED', 0),
('user019', 'user019@example.com', 'ACTIVE', 0),
('user020', 'user020@example.com', 'ACTIVE', 0);

-- Generate more users (80 additional)
INSERT INTO user (username, email, status, deleted)
SELECT
    CONCAT('user', LPAD(n, 3, '0')),
    CONCAT('user', LPAD(n, 3, '0'), '@example.com'),
    CASE
        WHEN n % 10 = 0 THEN 'INACTIVE'
        WHEN n % 15 = 0 THEN 'SUSPENDED'
        ELSE 'ACTIVE'
    END,
    CASE WHEN n % 20 = 0 THEN 1 ELSE 0 END
FROM (
    SELECT 21 + a.N + b.N * 10 AS n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) b
    WHERE 21 + a.N + b.N * 10 <= 100
) numbers;

-- Insert 500 test orders
INSERT INTO `order` (user_id, total_amount, status, order_time)
SELECT
    (n % 100) + 1,
    ROUND(10 + (RAND() * 990), 2),
    CASE
        WHEN n % 5 = 0 THEN 'COMPLETED'
        WHEN n % 5 = 1 THEN 'PENDING'
        WHEN n % 5 = 2 THEN 'PROCESSING'
        WHEN n % 5 = 3 THEN 'SHIPPED'
        ELSE 'CANCELLED'
    END,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
FROM (
    SELECT a.N + b.N * 10 + c.N * 100 AS n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) c
    WHERE a.N + b.N * 10 + c.N * 100 < 500
) numbers;

-- Insert 50 test products
INSERT INTO product (name, price, stock, category_id) VALUES
('Product 001', 29.99, 100, 1),
('Product 002', 49.99, 50, 1),
('Product 003', 19.99, 200, 2),
('Product 004', 99.99, 30, 2),
('Product 005', 39.99, 150, 3),
('Product 006', 59.99, 80, 3),
('Product 007', 79.99, 40, 1),
('Product 008', 24.99, 120, 2),
('Product 009', 89.99, 25, 3),
('Product 010', 34.99, 90, 1);

-- Generate more products (40 additional)
INSERT INTO product (name, price, stock, category_id)
SELECT
    CONCAT('Product ', LPAD(n, 3, '0')),
    ROUND(10 + (RAND() * 190), 2),
    FLOOR(10 + (RAND() * 190)),
    ((n - 1) % 5) + 1
FROM (
    SELECT 11 + a.N + b.N * 10 AS n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3) b
    WHERE 11 + a.N + b.N * 10 <= 50
) numbers;

-- Verify data
SELECT 'Users created:' AS info, COUNT(*) AS count FROM user;
SELECT 'Orders created:' AS info, COUNT(*) AS count FROM `order`;
SELECT 'Products created:' AS info, COUNT(*) AS count FROM product;

SELECT '✅ Database setup completed successfully!' AS status;
