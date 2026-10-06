package com.bizpos.controller;

import com.bizpos.dto.DashboardSummaryResponse;
import com.bizpos.dto.LowStockProductResponse;
import com.bizpos.dto.RevenueChartResponse;
import com.bizpos.dto.TopProductResponse;
import com.bizpos.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final DashboardService dashboardService;

    /**
     * 1. Thống kê KPI tổng quan (Doanh thu, số đơn, AOV, số SP sắp hết)
     * GET /api/dashboard/summary?startDate=2026-10-01&endDate=2026-10-07
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.getSummary(startDate, endDate));
    }

    /**
     * 2. Dữ liệu biểu đồ doanh thu theo ngày
     * GET /api/dashboard/revenue?startDate=2026-10-01&endDate=2026-10-07
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueChartResponse> getRevenueChart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.getRevenueChart(startDate, endDate));
    }

    /**
     * 3. Top sản phẩm bán chạy nhất theo số lượng bán
     * GET /api/dashboard/top-products?startDate=2026-10-01&endDate=2026-10-07&limit=5
     */
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductResponse>> getTopSellingProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getTopSellingProducts(startDate, endDate, limit));
    }

    /**
     * 4. Danh sách sản phẩm sắp hết hàng (tồn kho <= threshold)
     * GET /api/dashboard/low-stock?threshold=5
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockProductResponse>> getLowStockProducts(
            @RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(dashboardService.getLowStockProducts(threshold));
    }
}
