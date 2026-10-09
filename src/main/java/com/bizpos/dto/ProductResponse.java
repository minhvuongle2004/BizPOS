package com.bizpos.dto;

import com.bizpos.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer stockQuantity;
    private String size;
    private String color;
    private String material;
    private CategoryResponse category;
    private List<ProductVariantResponse> variants;
    private Integer totalStockQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        if (product == null) {
            return null;
        }

        List<ProductVariantResponse> variantResponses = Collections.emptyList();
        int totalStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            variantResponses = product.getVariants().stream()
                    .map(ProductVariantResponse::fromEntity)
                    .collect(Collectors.toList());
            totalStock = product.getVariants().stream()
                    .mapToInt(v -> v.getStockQuantity() != null ? v.getStockQuantity() : 0)
                    .sum();
        }

        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .stockQuantity(product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                .size(product.getSize())
                .color(product.getColor())
                .material(product.getMaterial())
                .category(CategoryResponse.fromEntity(product.getCategory()))
                .variants(variantResponses)
                .totalStockQuantity(totalStock)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
