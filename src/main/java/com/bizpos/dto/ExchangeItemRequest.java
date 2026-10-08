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

    @NotNull(message = "Vui lòng chọn sản phẩm muốn đổi lấy mới")
    private Long productId;

    @NotNull(message = "Vui lòng nhập số lượng đổi lấy mới")
    @Min(value = 1, message = "Số lượng đổi lấy mới phải lớn hơn hoặc bằng 1")
    private Integer quantity;
}
