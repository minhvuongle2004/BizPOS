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

    @NotNull(message = "Vui lòng cung cấp productId cho từng mục hàng!")
    private Long productId;

    @NotNull(message = "Số lượng mua không được để trống!")
    @Min(value = 1, message = "Số lượng mua cho từng sản phẩm phải lớn hơn 0!")
    private Integer quantity;
}
