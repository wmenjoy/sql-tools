-- SQL Server Syntax Samples

-- Simple SELECT with TOP
SELECT TOP 10 id, name FROM [users] WHERE age > 18;

-- UPDATE with square bracket identifiers
UPDATE [products] SET [price] = 99.99 WHERE [product_id] = 1;

-- DELETE with NOLOCK hint
DELETE FROM orders WHERE user_id = 123 AND status = 'cancelled';

-- INSERT with square brackets
INSERT INTO [users] (id, name, email) VALUES (1, 'John', 'john@example.com');

-- Complex query with JOIN and TOP
SELECT TOP 20 u.id, u.name, o.order_id 
FROM [users] u 
INNER JOIN [orders] o ON u.id = o.user_id 
WHERE u.status = 'active' 
ORDER BY u.id;

-- Subquery with IN clause
SELECT * FROM products WHERE category_id IN (SELECT id FROM categories WHERE active = 1);


















