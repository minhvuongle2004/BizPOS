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

    @NotNull(message = "Vui lòng chọn danh mục cho sản phẩm!")
    private Long categoryId;
}
