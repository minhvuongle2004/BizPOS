package com.bizpos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class CategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống!")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự!")
    private String name;

    @Size(max = 255, message = "Mô tả danh mục không được vượt quá 255 ký tự!")
    private String description;
}
