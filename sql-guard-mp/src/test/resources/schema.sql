-- Test schema for MyBatis-Plus integration tests

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    age INT,
    email VARCHAR(200),
    status VARCHAR(50),
    deleted INT DEFAULT 0 COMMENT 'Logic delete flag: 0=active, 1=deleted'
);

-- Insert test data
INSERT INTO user (id, name, age, email, status, deleted) VALUES
(1, 'Alice', 25, 'alice@example.com', 'active', 0),
(2, 'Bob', 30, 'bob@example.com', 'active', 0),
(3, 'Charlie', 35, 'charlie@example.com', 'inactive', 0),
(4, 'David', 28, 'david@example.com', 'active', 1);









