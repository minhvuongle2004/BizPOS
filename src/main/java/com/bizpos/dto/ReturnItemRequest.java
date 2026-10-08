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
public class ReturnItemRequest {

    @NotNull(message = "Vui lòng chọn sản phẩm muốn trả lại")
    private Long productId;

    @NotNull(message = "Vui lòng nhập số lượng trả lại")
    @Min(value = 1, message = "Số lượng trả lại phải lớn hơn hoặc bằng 1")
    private Integer quantity;

    private ReturnReason reason;

    private String note;
}
