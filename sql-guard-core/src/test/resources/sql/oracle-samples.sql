-- Oracle Syntax Samples

-- Simple SELECT with ROWNUM
SELECT id, name FROM users WHERE age > 18 AND ROWNUM <= 10;

-- UPDATE with dual table reference
UPDATE products SET price = 99.99 WHERE product_id = 1;

-- DELETE with outer join syntax
DELETE FROM orders WHERE user_id = 123 AND status = 'cancelled';

-- INSERT into table
INSERT INTO users (id, name, email) VALUES (1, 'John', 'john@example.com');

-- Complex query with outer join (+)
SELECT u.id, u.name, o.order_id 
FROM users u, orders o 
WHERE u.id = o.user_id(+) 
AND u.status = 'active' 
AND ROWNUM <= 20;

-- Subquery with IN clause
SELECT * FROM products WHERE category_id IN (SELECT id FROM categories WHERE active = 1);







