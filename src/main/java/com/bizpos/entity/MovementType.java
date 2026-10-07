package com.bizpos.entity;

public enum MovementType {
    SALE("Xuất bán hàng"),
    IMPORT("Nhập kho"),
    RETURN("Hoàn hàng / Hủy đơn"),
    ADJUSTMENT("Kiểm kê / Điều chỉnh thủ công");

    private final String description;

    MovementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
