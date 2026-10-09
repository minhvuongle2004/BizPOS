package com.bizpos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "Mã sản phẩm không được để trống!")
    @Size(max = 50, message = "Mã sản phẩm không được vượt quá 50 ký tự!")
    private String code;

    @NotBlank(message = "Tên sản phẩm không được để trống!")
    @Size(max = 150, message = "Tên sản phẩm không được vượt quá 150 ký tự!")
    private String name;

    @NotNull(message = "Giá bán không được để trống!")
    @Positive(message = "Giá bán sản phẩm phải lớn hơn 0!")
    private BigDecimal price;

    @Size(max = 500, message = "Mô tả sản phẩm không được vượt quá 500 ký tự!")
    private String description;

    @jakarta.validation.constraints.Min(value = 0, message = "Số lượng tồn kho không được âm!")
    private Integer stockQuantity;

    @Size(max = 30, message = "Kích cỡ sản phẩm không được vượt quá 30 ký tự!")
    private String size;

    @Size(max = 50, message = "Màu sắc sản phẩm không được vượt quá 50 ký tự!")
    private String color;

    @Size(max = 100, message = "Chất liệu sản phẩm không được vượt quá 100 ký tự!")
    private String material;

    @NotNull(message = "Vui lòng chọn danh mục cho sản phẩm!")
    private Long categoryId;

    /**
     * Danh sách biến thể chi tiết (nếu người dùng cấu hình thủ công từng SKU)
     */
    private java.util.List<ProductVariantRequest> variants;

    /**
     * Danh sách kích cỡ để sinh ma trận tự động (VD: ["S", "M", "L"])
     */
    private java.util.List<String> sizes;

    /**
     * Danh sách màu sắc để sinh ma trận tự động (VD: ["Đen", "Trắng"])
     */
    private java.util.List<String> colors;
}
