package com.bizpos.enums;

public enum ReturnType {
    RETURN_ONLY("Trả hàng hoàn tiền"),
    EXCHANGE("Đổi hàng (Đổi size / Đổi mẫu)");

    private final String description;

    ReturnType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
