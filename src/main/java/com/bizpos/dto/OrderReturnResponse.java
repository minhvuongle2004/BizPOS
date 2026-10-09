package com.bizpos.dto;

import com.bizpos.entity.OrderReturn;
import com.bizpos.enums.ReturnReason;
import com.bizpos.enums.ReturnStatus;
import com.bizpos.enums.ReturnType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnResponse {
    private Long id;
    private String returnCode;
    private Long orderId;
    private String orderCode;
    private LocalDateTime orderDate;
    private String customerName;
    private String customerPhone;
    private ReturnType returnType;
    private String returnTypeDescription;
    private ReturnStatus status;
    private String statusDescription;
    private BigDecimal totalRefundAmount;
    private BigDecimal totalExchangeAmount;
    private BigDecimal netAmount;
    private ReturnReason reason;
    private String reasonDescription;
    private String note;
    private String performedBy;
    private LocalDateTime createdAt;
    private List<OrderReturnItemResponse> returnItems;
    private List<OrderExchangeItemResponse> exchangeItems;

    public static OrderReturnResponse fromEntity(OrderReturn entity) {
        if (entity == null) return null;

        String custName = "Khách lẻ";
        String custPhone = "—";
        if (entity.getOrder() != null && entity.getOrder().getCustomer() != null) {
            custName = entity.getOrder().getCustomer().getFullName();
            custPhone = entity.getOrder().getCustomer().getPhone();
        }

        java.util.Set<Long> returnedProductIds = entity.getReturnItems() != null ?
                entity.getReturnItems().stream()
                        .map(ri -> ri.getProduct() != null ? ri.getProduct().getId() : null)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()) :
                java.util.Collections.emptySet();

        List<OrderExchangeItemResponse> exchangeResponses = entity.getExchangeItems() != null ?
                entity.getExchangeItems().stream().map(ei -> {
                    OrderExchangeItemResponse resp = OrderExchangeItemResponse.fromEntity(ei);
                    if (resp != null && ei.getProduct() != null) {
                        resp.setIsSameModel(returnedProductIds.contains(ei.getProduct().getId()));
                    }
                    return resp;
                }).collect(Collectors.toList()) :
                List.of();

        return OrderReturnResponse.builder()
                .id(entity.getId())
                .returnCode(entity.getReturnCode())
                .orderId(entity.getOrder() != null ? entity.getOrder().getId() : null)
                .orderCode(entity.getOrder() != null ? entity.getOrder().getOrderCode() : null)
                .orderDate(entity.getOrder() != null ? entity.getOrder().getOrderDate() : null)
                .customerName(custName)
                .customerPhone(custPhone)
                .returnType(entity.getReturnType())
                .returnTypeDescription(entity.getReturnType() != null ? entity.getReturnType().getDescription() : null)
                .status(entity.getStatus())
                .statusDescription(entity.getStatus() != null ? entity.getStatus().getDescription() : null)
                .totalRefundAmount(entity.getTotalRefundAmount())
                .totalExchangeAmount(entity.getTotalExchangeAmount())
                .netAmount(entity.getNetAmount())
                .reason(entity.getReason())
                .reasonDescription(entity.getReason() != null ? entity.getReason().getDescription() : null)
                .note(entity.getNote())
                .performedBy(entity.getPerformedBy())
                .createdAt(entity.getCreatedAt())
                .returnItems(entity.getReturnItems() != null ?
                        entity.getReturnItems().stream().map(OrderReturnItemResponse::fromEntity).collect(Collectors.toList()) :
                        List.of())
                .exchangeItems(exchangeResponses)
                .build();
    }
}
