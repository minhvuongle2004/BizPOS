package com.bizpos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {

    /**
     * ID sản phẩm (Dùng khi mua sản phẩm đơn hoặc client cũ gửi lên để tương thích ngược)
     */
    private Long productId;

    /**
     * ID biến thể cụ thể (Size, Color, SKU)
     */
    private Long variantId;

    @NotNull(message = "Số lượng mua không được để trống!")
    @Min(value = 1, message = "Số lượng mua cho từng sản phẩm phải lớn hơn 0!")
    private Integer quantity;
}
