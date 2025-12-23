-- Test schema for Druid integration tests
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY,
    user_id INT,
    amount DECIMAL(10, 2),
    status VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Insert test data
INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com');
INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com');
INSERT INTO users (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com');

INSERT INTO orders (id, user_id, amount, status) VALUES (1, 1, 100.00, 'COMPLETED');
INSERT INTO orders (id, user_id, amount, status) VALUES (2, 1, 200.00, 'PENDING');
INSERT INTO orders (id, user_id, amount, status) VALUES (3, 2, 150.00, 'COMPLETED');














