-- MySQL Syntax Samples

-- Simple SELECT with LIMIT
SELECT id, name FROM `users` WHERE age > 18 LIMIT 10;

-- UPDATE with backtick identifiers
UPDATE `products` SET `price` = 99.99 WHERE `product_id` = 1;

-- DELETE with UNSIGNED column
DELETE FROM orders WHERE user_id = 123 AND status = 'cancelled';

-- INSERT with multiple values
INSERT INTO users (id, name, age) VALUES (1, 'John', 25), (2, 'Jane', 30);

-- Complex query with JOIN and LIMIT
SELECT u.id, u.name, o.order_id 
FROM `users` u 
INNER JOIN `orders` o ON u.id = o.user_id 
WHERE u.status = 'active' 
LIMIT 20 OFFSET 10;

-- Subquery with IN clause
SELECT * FROM products WHERE category_id IN (SELECT id FROM categories WHERE active = 1);








