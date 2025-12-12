-- PostgreSQL Syntax Samples

-- Simple SELECT with LIMIT/OFFSET
SELECT id, name FROM users WHERE age > 18 LIMIT 10 OFFSET 5;

-- UPDATE with type cast
UPDATE products SET price = 99.99::numeric WHERE product_id = 1;

-- DELETE with boolean column
DELETE FROM orders WHERE user_id = 123 AND is_cancelled = true;

-- INSERT with RETURNING clause
INSERT INTO users (name, email) VALUES ('John', 'john@example.com');

-- Complex query with LEFT JOIN
SELECT u.id, u.name, o.order_id 
FROM users u 
LEFT JOIN orders o ON u.id = o.user_id 
WHERE u.status = 'active' 
LIMIT 20 OFFSET 10;

-- Subquery with EXISTS
SELECT * FROM products p WHERE EXISTS (SELECT 1 FROM categories c WHERE c.id = p.category_id AND c.active = true);
