-- ==============================================================
-- Flyway Migration V1.1: Tách bảng ProductVariant và Migrate dữ liệu
-- ==============================================================

-- 1. Tạo bảng product_variants
CREATE TABLE IF NOT EXISTS product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    barcode VARCHAR(100) NULL,
    size VARCHAR(30) NULL,
    color VARCHAR(50) NULL,
    price DECIMAL(15, 2) NOT NULL,
    cost_price DECIMAL(15, 2) NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT uk_product_variants_barcode UNIQUE (barcode),
    CONSTRAINT fk_product_variants_products
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Migrate toàn bộ sản phẩm hiện có sang bảng product_variants (1-1 an toàn)
INSERT INTO product_variants (product_id, sku, barcode, size, color, price, stock_quantity, is_active, created_at, updated_at)
SELECT 
    p.id, 
    p.code, 
    p.code, 
    p.size, 
    p.color, 
    p.price, 
    COALESCE(p.stock_quantity, 0), 
    TRUE, 
    p.created_at, 
    p.updated_at
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM product_variants pv WHERE pv.product_id = p.id
);

-- 3. Thêm cột variant_id vào các bảng liên quan nếu chưa có
-- 3.1. order_items
SET @exist_oi := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'variant_id');
SET @sql_oi := IF(@exist_oi = 0, 'ALTER TABLE order_items ADD COLUMN variant_id BIGINT NULL', 'SELECT 1');
PREPARE stmt_oi FROM @sql_oi;
EXECUTE stmt_oi;
DEALLOCATE PREPARE stmt_oi;

-- 3.2. stock_movements
SET @exist_sm := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_movements' AND COLUMN_NAME = 'variant_id');
SET @sql_sm := IF(@exist_sm = 0, 'ALTER TABLE stock_movements ADD COLUMN variant_id BIGINT NULL', 'SELECT 1');
PREPARE stmt_sm FROM @sql_sm;
EXECUTE stmt_sm;
DEALLOCATE PREPARE stmt_sm;

-- 3.3. order_return_items (nếu bảng tồn tại)
SET @table_ori := (SELECT COUNT(*) FROM information_schema.TABLES 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_return_items');
SET @exist_ori := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_return_items' AND COLUMN_NAME = 'variant_id');
SET @sql_ori := IF(@table_ori > 0 AND @exist_ori = 0, 'ALTER TABLE order_return_items ADD COLUMN variant_id BIGINT NULL', 'SELECT 1');
PREPARE stmt_ori FROM @sql_ori;
EXECUTE stmt_ori;
DEALLOCATE PREPARE stmt_ori;

-- 3.4. order_exchange_items (nếu bảng tồn tại)
SET @table_oei := (SELECT COUNT(*) FROM information_schema.TABLES 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_exchange_items');
SET @exist_oei := (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_exchange_items' AND COLUMN_NAME = 'variant_id');
SET @sql_oei := IF(@table_oei > 0 AND @exist_oei = 0, 'ALTER TABLE order_exchange_items ADD COLUMN variant_id BIGINT NULL', 'SELECT 1');
PREPARE stmt_oei FROM @sql_oei;
EXECUTE stmt_oei;
DEALLOCATE PREPARE stmt_oei;

-- 4. Đồng bộ dữ liệu variant_id từ product_id đã migrate
UPDATE order_items oi
JOIN product_variants pv ON oi.product_id = pv.product_id
SET oi.variant_id = pv.id
WHERE oi.variant_id IS NULL;

UPDATE stock_movements sm
JOIN product_variants pv ON sm.product_id = pv.product_id
SET sm.variant_id = pv.id
WHERE sm.variant_id IS NULL;

SET @update_ori := IF(@table_ori > 0, 
    'UPDATE order_return_items ori JOIN product_variants pv ON ori.product_id = pv.product_id SET ori.variant_id = pv.id WHERE ori.variant_id IS NULL', 
    'SELECT 1');
PREPARE stmt_up_ori FROM @update_ori;
EXECUTE stmt_up_ori;
DEALLOCATE PREPARE stmt_up_ori;

SET @update_oei := IF(@table_oei > 0, 
    'UPDATE order_exchange_items oei JOIN product_variants pv ON oei.product_id = pv.product_id SET oei.variant_id = pv.id WHERE oei.variant_id IS NULL', 
    'SELECT 1');
PREPARE stmt_up_oei FROM @update_oei;
EXECUTE stmt_up_oei;
DEALLOCATE PREPARE stmt_up_oei;
