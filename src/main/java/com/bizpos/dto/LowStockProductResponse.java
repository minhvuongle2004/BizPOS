package com.bizpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockProductResponse {

    private Long id;
    private String name;
    private String code;
    private Integer stockQuantity;
    private String status;
    private String categoryName;
}
