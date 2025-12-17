-- H2 test database schema

CREATE TABLE IF NOT EXISTS "user" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "order" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL
);

-- Insert test data
INSERT INTO "user" (username, email, status, deleted) VALUES
('testuser1', 'test1@example.com', 'ACTIVE', 0),
('testuser2', 'test2@example.com', 'ACTIVE', 0),
('testuser3', 'test3@example.com', 'INACTIVE', 0);

INSERT INTO "order" (user_id, total_amount, status) VALUES
(1, 99.99, 'COMPLETED'),
(1, 149.99, 'PENDING'),
(2, 79.99, 'COMPLETED');

INSERT INTO product (name, price, stock, category_id) VALUES
('Test Product 1', 29.99, 100, 1),
('Test Product 2', 49.99, 50, 2),
('Test Product 3', 19.99, 200, 1);
