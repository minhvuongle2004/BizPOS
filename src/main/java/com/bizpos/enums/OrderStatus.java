package com.bizpos.enums;

public enum OrderStatus {
    COMPLETED("Hoàn thành"),
    CANCELLED("Đã hủy"),
    PARTIALLY_RETURNED("Đã hoàn trả một phần"),
    RETURNED("Đã hoàn trả");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
