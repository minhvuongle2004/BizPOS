package com.bizpos.dto;

import com.bizpos.entity.ProductVariant;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productCode;
    private String sku;
    private String barcode;
    private String size;
    private String color;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer stockQuantity;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductVariantResponse fromEntity(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct() != null ? variant.getProduct().getId() : null)
                .productName(variant.getProduct() != null ? variant.getProduct().getName() : null)
                .productCode(variant.getProduct() != null ? variant.getProduct().getCode() : null)
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .size(variant.getSize())
                .color(variant.getColor())
                .price(variant.getPrice())
                .costPrice(variant.getCostPrice())
                .stockQuantity(variant.getStockQuantity() != null ? variant.getStockQuantity() : 0)
                .isActive(variant.getIsActive() != null ? variant.getIsActive() : true)
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }
}
