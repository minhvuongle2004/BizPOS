package com.bizpos.dto;

import com.bizpos.enums.ReturnReason;
import com.bizpos.enums.ReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class OrderReturnRequest {

    @NotNull(message = "Mã hóa đơn gốc không được để trống")
    private String orderCode;

    private ReturnType returnType;

    @NotNull(message = "Vui lòng chọn lý do đổi / trả hàng")
    private ReturnReason reason;

    public void setMainReason(ReturnReason mainReason) {
        if (this.reason == null) {
            this.reason = mainReason;
        }
    }

    private String note;

    @NotEmpty(message = "Danh sách sản phẩm trả lại không được để trống")
    @Valid
    @Builder.Default
    private List<ReturnItemRequest> returnItems = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<ExchangeItemRequest> exchangeItems = new ArrayList<>();
}
