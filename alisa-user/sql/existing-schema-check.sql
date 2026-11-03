-- Read-only preflight for existing installations; run against the existing account database.
SHOW COLUMNS FROM tb_user LIKE 'user_password';
SHOW INDEX FROM tb_user;
SELECT user_name, COUNT(*) AS duplicate_count FROM tb_user GROUP BY user_name HAVING COUNT(*) > 1;
SELECT user_email, COUNT(*) AS duplicate_count FROM tb_user GROUP BY user_email HAVING COUNT(*) > 1;
-- After checking password column capacity (>= 60) and resolving duplicates, add missing unique indexes:
-- ALTER TABLE tb_user ADD UNIQUE KEY uk_user_name (user_name);
-- ALTER TABLE tb_user ADD UNIQUE KEY uk_user_email (user_email);
