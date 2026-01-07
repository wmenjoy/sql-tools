-- H2 test database schema for SqlSafetyInterceptor integration tests

-- Drop tables if they exist
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

-- Create users table
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create orders table
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_name VARCHAR(200),
    amount DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Create products table
CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(50),
    stock INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert test data
INSERT INTO users (name, email, age, status) VALUES
    ('Alice', 'alice@example.com', 25, 'ACTIVE'),
    ('Bob', 'bob@example.com', 30, 'ACTIVE'),
    ('Charlie', 'charlie@example.com', 35, 'INACTIVE'),
    ('David', 'david@example.com', 28, 'ACTIVE'),
    ('Eve', 'eve@example.com', 32, 'ACTIVE');

INSERT INTO orders (user_id, product_name, amount, status) VALUES
    (1, 'Laptop', 1200.00, 'COMPLETED'),
    (1, 'Mouse', 25.00, 'COMPLETED'),
    (2, 'Keyboard', 75.00, 'PENDING'),
    (3, 'Monitor', 300.00, 'CANCELLED'),
    (4, 'Headphones', 150.00, 'COMPLETED');

INSERT INTO products (name, price, category, stock) VALUES
    ('Laptop Pro', 1500.00, 'Electronics', 10),
    ('Wireless Mouse', 30.00, 'Electronics', 50),
    ('Mechanical Keyboard', 100.00, 'Electronics', 30),
    ('4K Monitor', 400.00, 'Electronics', 15),
    ('Noise-Cancelling Headphones', 200.00, 'Electronics', 25);
















