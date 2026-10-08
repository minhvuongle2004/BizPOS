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
    private String productCode;
    private String productName;
    private String size;
    private String color;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public static OrderExchangeItemResponse fromEntity(OrderExchangeItem item) {
        if (item == null) return null;
        return OrderExchangeItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
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
