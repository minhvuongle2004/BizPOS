package com.bizpos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeItemRequest {

    /**
     * ID sản phẩm mẫu (dùng cho client cũ hoặc tương thích ngược)
     */
    private Long productId;

    /**
     * ID biến thể cụ thể muốn đổi lấy (Size, Màu, SKU)
     */
    private Long variantId;

    @NotNull(message = "Vui lòng nhập số lượng đổi lấy mới")
    @Min(value = 1, message = "Số lượng đổi lấy mới phải lớn hơn hoặc bằng 1")
    private Integer quantity;
}
