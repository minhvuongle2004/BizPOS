package com.bizpos.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReconciliationReport {
    private Long productId;
    private String productCode;
    private String productName;
    private Integer initialStock;
    private Integer totalDelta;
    private Integer calculatedStock; // initialStock + totalDelta
    private Integer currentStock;
    private int movementCount;
    private boolean isReconciled;
    private String message;
}
