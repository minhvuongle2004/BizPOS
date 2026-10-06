package com.bizpos.service;

import com.bizpos.dto.DashboardSummaryResponse;
import com.bizpos.dto.LowStockProductResponse;
import com.bizpos.dto.RevenueChartResponse;
import com.bizpos.dto.TopProductResponse;

import java.time.LocalDate;
import java.util.List;

public interface DashboardService {

    DashboardSummaryResponse getSummary(LocalDate startDate, LocalDate endDate);

    RevenueChartResponse getRevenueChart(LocalDate startDate, LocalDate endDate);

    List<TopProductResponse> getTopSellingProducts(LocalDate startDate, LocalDate endDate, int limit);

    List<LowStockProductResponse> getLowStockProducts(int threshold);
}
