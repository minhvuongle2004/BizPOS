package com.bizpos.dto;

import com.bizpos.entity.OrderReturnItem;
import com.bizpos.enums.ReturnReason;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnItemResponse {
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
    private ReturnReason reason;
    private String reasonDescription;
    private String note;

    public static OrderReturnItemResponse fromEntity(OrderReturnItem item) {
        if (item == null) return null;
        return OrderReturnItemResponse.builder()
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
                .reason(item.getReason())
                .reasonDescription(item.getReason() != null ? item.getReason().getDescription() : null)
                .note(item.getNote())
                .build();
    }
}
