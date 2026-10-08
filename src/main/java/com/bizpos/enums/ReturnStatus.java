package com.bizpos.enums;

public enum ReturnStatus {
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy");

    private final String description;

    ReturnStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
