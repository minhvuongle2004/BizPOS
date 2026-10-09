package com.bizpos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    private Long id; // Null nếu là biến thể mới
    private String sku;
    private String barcode;
    private String size;
    private String color;

    @DecimalMin(value = "0.0", message = "Giá biến thể không được âm!")
    private BigDecimal price; // Nếu để trống, tự động kế thừa giá của mẫu cha

    private BigDecimal costPrice;

    @Min(value = 0, message = "Số lượng tồn kho không được âm!")
    private Integer stockQuantity;

    private Boolean isActive;
}
