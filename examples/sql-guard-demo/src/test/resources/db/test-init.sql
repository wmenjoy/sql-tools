-- SQL Guard Demo Test Database Initialization Script (H2 compatible)
-- Creates tables and populates test data for integration tests

-- Drop existing tables if they exist (H2 syntax with quoted identifiers)
DROP TABLE IF EXISTS "order";
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS "user";

-- User table (using double quotes to escape reserved word)
CREATE TABLE "user" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for user table
CREATE INDEX idx_user_username ON "user" (username);
CREATE INDEX idx_user_email ON "user" (email);
CREATE INDEX idx_user_status ON "user" (status);
CREATE INDEX idx_user_deleted ON "user" (deleted);

-- Order table
CREATE TABLE "order" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for order table
CREATE INDEX idx_order_user_id ON "order" (user_id);
CREATE INDEX idx_order_status ON "order" (status);
CREATE INDEX idx_order_time ON "order" (order_time);

-- Product table
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL
);

-- Create indexes for product table
CREATE INDEX idx_product_name ON product (name);
CREATE INDEX idx_product_category ON product (category_id);

-- Insert test users (100 users)
INSERT INTO "user" (username, email, status, deleted) VALUES
('user001', 'user001@example.com', 'ACTIVE', 0),
('user002', 'user002@example.com', 'ACTIVE', 0),
('user003', 'user003@example.com', 'INACTIVE', 0),
('user004', 'user004@example.com', 'ACTIVE', 0),
('user005', 'user005@example.com', 'ACTIVE', 0),
('user006', 'user006@example.com', 'SUSPENDED', 0),
('user007', 'user007@example.com', 'ACTIVE', 0),
('user008', 'user008@example.com', 'ACTIVE', 0),
('user009', 'user009@example.com', 'ACTIVE', 1),
('user010', 'user010@example.com', 'INACTIVE', 0),
('user011', 'user011@example.com', 'ACTIVE', 0),
('user012', 'user012@example.com', 'ACTIVE', 0),
('user013', 'user013@example.com', 'ACTIVE', 0),
('user014', 'user014@example.com', 'ACTIVE', 0),
('user015', 'user015@example.com', 'SUSPENDED', 0),
('user016', 'user016@example.com', 'ACTIVE', 0),
('user017', 'user017@example.com', 'ACTIVE', 0),
('user018', 'user018@example.com', 'ACTIVE', 0),
('user019', 'user019@example.com', 'ACTIVE', 0),
('user020', 'user020@example.com', 'INACTIVE', 1),
('user021', 'user021@example.com', 'ACTIVE', 0),
('user022', 'user022@example.com', 'ACTIVE', 0),
('user023', 'user023@example.com', 'ACTIVE', 0),
('user024', 'user024@example.com', 'ACTIVE', 0),
('user025', 'user025@example.com', 'ACTIVE', 0),
('user026', 'user026@example.com', 'ACTIVE', 0),
('user027', 'user027@example.com', 'ACTIVE', 0),
('user028', 'user028@example.com', 'ACTIVE', 0),
('user029', 'user029@example.com', 'ACTIVE', 0),
('user030', 'user030@example.com', 'INACTIVE', 0),
('user031', 'user031@example.com', 'ACTIVE', 0),
('user032', 'user032@example.com', 'ACTIVE', 0),
('user033', 'user033@example.com', 'ACTIVE', 0),
('user034', 'user034@example.com', 'ACTIVE', 0),
('user035', 'user035@example.com', 'ACTIVE', 0),
('user036', 'user036@example.com', 'ACTIVE', 0),
('user037', 'user037@example.com', 'ACTIVE', 0),
('user038', 'user038@example.com', 'ACTIVE', 0),
('user039', 'user039@example.com', 'ACTIVE', 0),
('user040', 'user040@example.com', 'INACTIVE', 1),
('user041', 'user041@example.com', 'ACTIVE', 0),
('user042', 'user042@example.com', 'ACTIVE', 0),
('user043', 'user043@example.com', 'ACTIVE', 0),
('user044', 'user044@example.com', 'ACTIVE', 0),
('user045', 'user045@example.com', 'SUSPENDED', 0),
('user046', 'user046@example.com', 'ACTIVE', 0),
('user047', 'user047@example.com', 'ACTIVE', 0),
('user048', 'user048@example.com', 'ACTIVE', 0),
('user049', 'user049@example.com', 'ACTIVE', 0),
('user050', 'user050@example.com', 'INACTIVE', 0);

-- Insert test orders (50 orders)
INSERT INTO "order" (user_id, total_amount, status) VALUES
(1, 99.99, 'COMPLETED'),
(2, 149.50, 'PENDING'),
(3, 75.00, 'PROCESSING'),
(4, 250.00, 'SHIPPED'),
(5, 50.00, 'CANCELLED'),
(6, 199.99, 'COMPLETED'),
(7, 89.99, 'PENDING'),
(8, 125.00, 'PROCESSING'),
(9, 300.00, 'SHIPPED'),
(10, 45.00, 'COMPLETED'),
(1, 120.00, 'COMPLETED'),
(2, 80.00, 'PENDING'),
(3, 200.00, 'PROCESSING'),
(4, 175.00, 'SHIPPED'),
(5, 95.00, 'CANCELLED'),
(6, 150.00, 'COMPLETED'),
(7, 220.00, 'PENDING'),
(8, 65.00, 'PROCESSING'),
(9, 180.00, 'SHIPPED'),
(10, 110.00, 'COMPLETED');

-- Insert test products (20 products)
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
('Product 010', 34.99, 90, 1),
('Product 011', 44.99, 70, 2),
('Product 012', 54.99, 60, 3),
('Product 013', 64.99, 55, 1),
('Product 014', 74.99, 45, 2),
('Product 015', 84.99, 35, 3),
('Product 016', 94.99, 28, 1),
('Product 017', 104.99, 22, 2),
('Product 018', 114.99, 18, 3),
('Product 019', 124.99, 15, 1),
('Product 020', 134.99, 12, 2);
