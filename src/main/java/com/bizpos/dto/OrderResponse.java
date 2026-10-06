package com.bizpos.dto;

import com.bizpos.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private CustomerResponse customer;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<OrderItemResponse> items = new ArrayList<>();

    public static OrderResponse fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream()
                        .map(OrderItemResponse::fromEntity)
                        .collect(Collectors.toList())
                : new ArrayList<>();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customer(order.getCustomer() != null ? CustomerResponse.fromEntity(order.getCustomer()) : null)
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
