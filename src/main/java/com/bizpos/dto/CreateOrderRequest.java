package com.bizpos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    private Long customerId;

    @Size(max = 500, message = "Ghi chú đơn hàng không được vượt quá 500 ký tự!")
    private String note;

    @Builder.Default
    private com.bizpos.enums.PaymentMethod paymentMethod = com.bizpos.enums.PaymentMethod.CASH;

    private java.math.BigDecimal amountPaid;

    @Size(max = 255, message = "Ghi chú thanh toán không được vượt quá 255 ký tự!")
    private String paymentNote;

    @NotEmpty(message = "Đơn hàng phải chứa ít nhất 1 sản phẩm!")
    @Valid
    @Builder.Default
    private List<OrderItemRequest> items = new ArrayList<>();
}
