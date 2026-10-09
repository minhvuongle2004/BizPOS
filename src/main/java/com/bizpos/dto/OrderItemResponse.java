package com.bizpos.dto;

import com.bizpos.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long id;
    private Long productId;
    private ProductResponse product;
    private Long variantId;
    private String variantSku;
    private String variantBarcode;
    private String size;
    private String color;
    private ProductVariantResponse variant;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null) {
            return null;
        }

        String itemSize = null;
        String itemColor = null;
        String sku = null;
        String barcode = null;

        if (item.getVariant() != null) {
            itemSize = item.getVariant().getSize();
            itemColor = item.getVariant().getColor();
            sku = item.getVariant().getSku();
            barcode = item.getVariant().getBarcode();
        } else if (item.getProduct() != null) {
            itemSize = item.getProduct().getSize();
            itemColor = item.getProduct().getColor();
            sku = item.getProduct().getCode();
            barcode = item.getProduct().getCode();
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .product(item.getProduct() != null ? ProductResponse.fromEntity(item.getProduct()) : null)
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .variantSku(sku)
                .variantBarcode(barcode)
                .size(itemSize)
                .color(itemColor)
                .variant(item.getVariant() != null ? ProductVariantResponse.fromEntity(item.getVariant()) : null)
                .productName(item.getProductName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
