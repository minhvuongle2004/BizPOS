-- ==============================================================
-- Flyway Migration V1.5: Thêm trường duyệt ngoại lệ vào order_returns
-- ==============================================================

SET @exist_col1 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_returns' AND COLUMN_NAME = 'is_policy_overridden');
SET @sql_col1 := IF(@exist_col1 = 0, 'ALTER TABLE order_returns ADD COLUMN is_policy_overridden BOOLEAN DEFAULT FALSE', 'SELECT 1');
PREPARE stmt1 FROM @sql_col1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @exist_col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_returns' AND COLUMN_NAME = 'override_reason');
SET @sql_col2 := IF(@exist_col2 = 0, 'ALTER TABLE order_returns ADD COLUMN override_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt2 FROM @sql_col2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @exist_col3 := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_returns' AND COLUMN_NAME = 'approved_by');
SET @sql_col3 := IF(@exist_col3 = 0, 'ALTER TABLE order_returns ADD COLUMN approved_by VARCHAR(50) NULL', 'SELECT 1');
PREPARE stmt3 FROM @sql_col3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;
