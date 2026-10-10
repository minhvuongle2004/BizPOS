-- V1.4: Add DB Triggers to enforce append-only immutability on stock_movements table
-- Ngăn chặn triệt để mọi hành vi UPDATE hoặc DELETE trực tiếp trên bảng stock_movements

DROP TRIGGER IF EXISTS trg_stock_movements_prevent_update;
DROP TRIGGER IF EXISTS trg_stock_movements_prevent_delete;

CREATE TRIGGER trg_stock_movements_prevent_update
BEFORE UPDATE ON stock_movements
FOR EACH ROW
SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'LỖI BẢO MẬT: Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm chỉnh sửa dữ liệu!';

CREATE TRIGGER trg_stock_movements_prevent_delete
BEFORE DELETE ON stock_movements
FOR EACH ROW
SIGNAL SQLSTATE '45000'
SET MESSAGE_TEXT = 'LỖI BẢO MẬT: Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm xóa dữ liệu!';
