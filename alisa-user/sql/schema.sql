-- New installations only. Existing installations continue to use their current db_user.
-- Never initialize from the old SmallOKR dump: it contains account data and DROP statements.
CREATE DATABASE IF NOT EXISTS db_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_user;
CREATE TABLE IF NOT EXISTS tb_user (
  user_id CHAR(32) NOT NULL,
  user_name VARCHAR(45) NOT NULL,
  user_password VARCHAR(100) NOT NULL,
  user_email VARCHAR(255) NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_name (user_name),
  UNIQUE KEY uk_user_email (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
