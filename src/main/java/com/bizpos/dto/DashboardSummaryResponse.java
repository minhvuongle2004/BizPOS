package com.bizpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private BigDecimal totalRevenue; // Doanh thu thuần (Net Revenue)
    private Long totalOrders;
    private BigDecimal averageOrderValue;
    private Long lowStockCount;

    // Chi tiết minh bạch doanh thu bán hàng & đổi trả
    private BigDecimal grossRevenue;
    private BigDecimal totalRefundAmount;
    private BigDecimal totalExchangeAmount;
    private BigDecimal netReturnAdjustment;
}
