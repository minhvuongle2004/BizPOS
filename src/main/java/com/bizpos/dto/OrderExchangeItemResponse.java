package com.bizpos.dto;

import com.bizpos.entity.OrderExchangeItem;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderExchangeItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String variantSku;
    private String variantBarcode;
    private String productCode;
    private String productName;
    private String size;
    private String color;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private Boolean isSameModel;

    public static OrderExchangeItemResponse fromEntity(OrderExchangeItem item) {
        if (item == null) return null;
        return OrderExchangeItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .variantSku(item.getVariant() != null ? item.getVariant().getSku() : null)
                .variantBarcode(item.getVariant() != null ? item.getVariant().getBarcode() : null)
                .productCode(item.getProductCode())
                .productName(item.getProductName())
                .size(item.getSize())
                .color(item.getColor())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .build();
    }
}
