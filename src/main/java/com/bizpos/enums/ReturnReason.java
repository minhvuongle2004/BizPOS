package com.bizpos.enums;

public enum ReturnReason {
    WRONG_SIZE("Mặc chật / Rộng kích cỡ (Size không vừa)"),
    DEFECTIVE("Lỗi sản phẩm / Vải lỗi / Đường may hỏng"),
    COLOR_MISMATCH("Không ưng màu sắc thực tế"),
    CUSTOMER_CHANGE_MIND("Khách đổi ý / Không còn nhu cầu"),
    OTHER("Lý do khác");

    private final String description;

    ReturnReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
