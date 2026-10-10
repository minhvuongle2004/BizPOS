package com.bizpos.dto;

import com.bizpos.enums.ReturnReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class ReturnItemRequest {

    /**
     * ID dòng hóa đơn gốc (khuyên dùng để định danh chính xác dòng được trả)
     */
    private Long orderItemId;

    /**
     * ID sản phẩm mẫu (dùng cho client cũ hoặc tương thích ngược)
     */
    private Long productId;

    /**
     * ID biến thể cụ thể muốn trả lại
     */
    private Long variantId;

    @NotNull(message = "Vui lòng nhập số lượng trả lại")
    @Min(value = 1, message = "Số lượng trả lại phải lớn hơn hoặc bằng 1")
    private Integer quantity;

    private ReturnReason reason;

    private String note;
}
