-- ==============================================================
-- Flyway Migration V1.3: Thêm các trường thanh toán vào bảng orders
-- Phục vụ thanh toán Tiền mặt (CASH) và Chuyển khoản (BANK_TRANSFER)
-- ==============================================================

-- 1. Thêm cột payment_method vào orders nếu chưa có
SET @exist_pm := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'payment_method');
SET @sql_pm := IF(@exist_pm = 0, "ALTER TABLE orders ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH'", 'SELECT 1');
PREPARE stmt_pm FROM @sql_pm;
EXECUTE stmt_pm;
DEALLOCATE PREPARE stmt_pm;

-- 2. Thêm cột payment_status vào orders nếu chưa có
SET @exist_ps := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'payment_status');
SET @sql_ps := IF(@exist_ps = 0, "ALTER TABLE orders ADD COLUMN payment_status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'", 'SELECT 1');
PREPARE stmt_ps FROM @sql_ps;
EXECUTE stmt_ps;
DEALLOCATE PREPARE stmt_ps;

-- 3. Thêm cột amount_paid vào orders nếu chưa có
SET @exist_ap := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'amount_paid');
SET @sql_ap := IF(@exist_ap = 0, 'ALTER TABLE orders ADD COLUMN amount_paid DECIMAL(15,2) NOT NULL DEFAULT 0.00', 'SELECT 1');
PREPARE stmt_ap FROM @sql_ap;
EXECUTE stmt_ap;
DEALLOCATE PREPARE stmt_ap;

-- 4. Thêm cột change_amount vào orders nếu chưa có
SET @exist_ca := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'change_amount');
SET @sql_ca := IF(@exist_ca = 0, 'ALTER TABLE orders ADD COLUMN change_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00', 'SELECT 1');
PREPARE stmt_ca FROM @sql_ca;
EXECUTE stmt_ca;
DEALLOCATE PREPARE stmt_ca;

-- 5. Thêm cột payment_note vào orders nếu chưa có
SET @exist_pn := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'payment_note');
SET @sql_pn := IF(@exist_pn = 0, 'ALTER TABLE orders ADD COLUMN payment_note VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt_pn FROM @sql_pn;
EXECUTE stmt_pn;
DEALLOCATE PREPARE stmt_pn;

-- 6. Backfill dữ liệu cũ: Cập nhật amount_paid = total_amount cho các đơn hàng cũ
UPDATE orders 
SET amount_paid = total_amount, 
    change_amount = 0.00,
    payment_method = 'CASH',
    payment_status = 'COMPLETED'
WHERE amount_paid = 0.00 AND total_amount > 0;
