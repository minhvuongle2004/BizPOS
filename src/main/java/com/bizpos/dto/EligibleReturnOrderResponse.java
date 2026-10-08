package com.bizpos.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleReturnOrderResponse {
    private Long orderId;
    private String orderCode;
    private LocalDateTime orderDate;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalAmount;
    private long daysSincePurchase;
    private boolean eligible;
    private String message;
    private List<EligibleReturnItemResponse> items;
}
