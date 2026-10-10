-- ==============================================================
-- Flyway Migration V1.2: Thêm order_item_id vào order_return_items
-- Giúp định danh chính xác dòng hóa đơn gốc được trả lại
-- ==============================================================

-- 1. Thêm cột order_item_id vào order_return_items nếu chưa có
SET @exist_ori := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_return_items' AND COLUMN_NAME = 'order_item_id');
SET @sql_ori := IF(@exist_ori = 0, 'ALTER TABLE order_return_items ADD COLUMN order_item_id BIGINT NULL', 'SELECT 1');
PREPARE stmt_ori FROM @sql_ori;
EXECUTE stmt_ori;
DEALLOCATE PREPARE stmt_ori;

-- 2. Thêm Foreign Key từ order_return_items sang order_items nếu chưa có
SET @exist_fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_return_items' AND CONSTRAINT_NAME = 'fk_order_return_items_order_items');
SET @sql_fk := IF(@exist_fk = 0, 
    'ALTER TABLE order_return_items ADD CONSTRAINT fk_order_return_items_order_items FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON UPDATE CASCADE ON DELETE SET NULL', 
    'SELECT 1');
PREPARE stmt_fk FROM @sql_fk;
EXECUTE stmt_fk;
DEALLOCATE PREPARE stmt_fk;

-- 3. Cập nhật dữ liệu cũ: Điền order_item_id cho các bản ghi trả hàng trong quá khứ
UPDATE order_return_items ori
JOIN order_returns r ON ori.order_return_id = r.id
JOIN order_items oi ON oi.order_id = r.order_id 
    AND oi.product_id = ori.product_id
    AND (ori.variant_id IS NULL OR oi.variant_id = ori.variant_id)
SET ori.order_item_id = oi.id
WHERE ori.order_item_id IS NULL;
