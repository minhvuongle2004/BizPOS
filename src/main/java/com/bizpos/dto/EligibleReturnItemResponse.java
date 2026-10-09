package com.bizpos.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleReturnItemResponse {
    private Long productId;
    private Long variantId;
    private String variantSku;
    private String variantBarcode;
    private String productCode;
    private String productName;
    private String size;
    private String color;
    private BigDecimal unitPrice;
    private int purchasedQuantity;
    private int alreadyReturnedQuantity;
    private int remainingQuantity;
    private boolean canReturn;
}
