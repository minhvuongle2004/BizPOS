package com.bizpos.service;

import com.bizpos.dto.DashboardSummaryResponse;
import com.bizpos.dto.LowStockProductResponse;
import com.bizpos.dto.RevenueChartResponse;
import com.bizpos.dto.TopProductResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.repository.OrderItemRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho DashboardService Business Logic")
public class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private LocalDate today;
    private Category category;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        category = Category.builder().id(1L).name("Cà phê").build();
    }

    // =========================================================================
    // 1. KPI SUMMARY & CALCULATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Summary: Tính toán chính xác doanh thu, số đơn, AOV và số sản phẩm sắp hết hàng")
    void getSummary_shouldCalculateCorrectKPIs_whenDataExists() {
        LocalDate start = today.minusDays(6);
        LocalDate end = today;

        when(orderRepository.calculateRevenueBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("1000000.00"));
        when(orderRepository.countOrdersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(4L);
        when(productRepository.countLowStockProducts(5))
                .thenReturn(3L);

        DashboardSummaryResponse response = dashboardService.getSummary(start, end);

        assertNotNull(response);
        assertEquals(new BigDecimal("1000000.00"), response.getTotalRevenue());
        assertEquals(4L, response.getTotalOrders());
        // AOV = 1,000,000 / 4 = 250,000
        assertEquals(new BigDecimal("250000"), response.getAverageOrderValue());
        assertEquals(3L, response.getLowStockCount());
    }

    @Test
    @DisplayName("Summary: Khi không có đơn hàng nào (totalOrders = 0) thì AOV = 0 (tránh chia cho 0)")
    void getSummary_shouldHandleZeroOrders_withAOVZero() {
        when(orderRepository.calculateRevenueBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(productRepository.countLowStockProducts(5))
                .thenReturn(0L);

        DashboardSummaryResponse response = dashboardService.getSummary(today, today);

        assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
        assertEquals(0L, response.getTotalOrders());
        assertEquals(BigDecimal.ZERO, response.getAverageOrderValue());
        assertEquals(0L, response.getLowStockCount());
    }

    @Test
    @DisplayName("Summary: Xử lý an toàn khi database trả về null cho doanh thu")
    void getSummary_shouldHandleNullRevenueFromDatabase() {
        when(orderRepository.calculateRevenueBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null);
        when(orderRepository.countOrdersBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(productRepository.countLowStockProducts(5))
                .thenReturn(2L);

        DashboardSummaryResponse response = dashboardService.getSummary(today, today);

        assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
        assertEquals(0L, response.getTotalOrders());
        assertEquals(BigDecimal.ZERO, response.getAverageOrderValue());
        assertEquals(2L, response.getLowStockCount());
    }

    @Test
    @DisplayName("Summary: Sử dụng đúng ngưỡng mặc định threshold = 5 cho sản phẩm sắp hết hàng")
    void getSummary_shouldUseDefaultThreshold5ForLowStock() {
        when(orderRepository.calculateRevenueBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any())).thenReturn(0L);
        when(productRepository.countLowStockProducts(5)).thenReturn(7L);

        DashboardSummaryResponse response = dashboardService.getSummary(today, today);

        assertEquals(7L, response.getLowStockCount());
        verify(productRepository).countLowStockProducts(5);
    }

    // =========================================================================
    // 2. DATE RANGE & NORMALIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Date Range: Khi cả startDate và endDate là null -> mặc định lấy 7 ngày gần nhất")
    void normalizeDateRange_shouldDefaultToLast7Days_whenBothDatesNull() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(orderRepository.calculateRevenueBetween(startCaptor.capture(), endCaptor.capture()))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any())).thenReturn(0L);
        when(productRepository.countLowStockProducts(5)).thenReturn(0L);

        dashboardService.getSummary(null, null);

        LocalDate expectedStart = today.minusDays(6);
        LocalDate expectedEnd = today;

        assertEquals(expectedStart.atStartOfDay(), startCaptor.getValue());
        assertEquals(expectedEnd.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    @DisplayName("Date Range: Khi chỉ truyền startDate -> endDate mặc định là hôm nay (Hỗ trợ lọc 30 ngày)")
    void normalizeDateRange_shouldUseTodayAsEndDate_whenOnlyStartDateProvided() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(orderRepository.calculateRevenueBetween(startCaptor.capture(), endCaptor.capture()))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any())).thenReturn(0L);
        when(productRepository.countLowStockProducts(5)).thenReturn(0L);

        LocalDate thirtyDaysAgo = today.minusDays(29);
        dashboardService.getSummary(thirtyDaysAgo, null);

        assertEquals(thirtyDaysAgo.atStartOfDay(), startCaptor.getValue());
        assertEquals(today.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    @DisplayName("Date Range: Lọc trong ngày hôm nay (Today: startDate = endDate = today)")
    void normalizeDateRange_shouldHandleSingleDayToday() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(orderRepository.calculateRevenueBetween(startCaptor.capture(), endCaptor.capture()))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any())).thenReturn(0L);
        when(productRepository.countLowStockProducts(5)).thenReturn(0L);

        dashboardService.getSummary(today, today);

        assertEquals(today.atStartOfDay(), startCaptor.getValue());
        assertEquals(today.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    @DisplayName("Date Range: Khi startDate sau endDate -> tự động đảo vị trí (swap) để khoảng ngày hợp lệ")
    void normalizeDateRange_shouldSwapDates_whenStartDateIsAfterEndDate() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(orderRepository.calculateRevenueBetween(startCaptor.capture(), endCaptor.capture()))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any())).thenReturn(0L);
        when(productRepository.countLowStockProducts(5)).thenReturn(0L);

        LocalDate d1 = LocalDate.of(2026, 10, 10);
        LocalDate d2 = LocalDate.of(2026, 10, 1);

        // Truyền d1 (ngày 10) trước d2 (ngày 1)
        dashboardService.getSummary(d1, d2);

        assertEquals(d2.atStartOfDay(), startCaptor.getValue());
        assertEquals(d1.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    // =========================================================================
    // 3. REVENUE CHART TESTS (ZERO-FILL & LABELS)
    // =========================================================================

    @Test
    @DisplayName("Revenue Chart: Khi không có order nào trong khoảng thời gian -> zero-fill đầy đủ tất cả các ngày")
    void getRevenueChart_shouldReturnZeroFilledData_whenNoOrdersInDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 3); // 3 ngày

        when(orderRepository.findDailyRevenueBetween(any(), any())).thenReturn(Collections.emptyList());

        RevenueChartResponse response = dashboardService.getRevenueChart(start, end);

        assertNotNull(response);
        assertEquals(3, response.getLabels().size());
        assertEquals(List.of("01/10", "02/10", "03/10"), response.getLabels());

        assertEquals(3, response.getData().size());
        assertEquals(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), response.getData());
        assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
    }

    @Test
    @DisplayName("Revenue Chart: Điền đúng doanh thu cho ngày có đơn và zero-fill các ngày trống")
    void getRevenueChart_shouldCorrectlyFillDailyRevenue_andZeroFillMissingDays() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 3);

        // Database trả về chỉ có ngày 2026-10-02 phát sinh doanh thu 250,000
        List<Object[]> rows = Collections.singletonList(
                new Object[]{"2026-10-02", new BigDecimal("250000.00")}
        );
        when(orderRepository.findDailyRevenueBetween(any(), any())).thenReturn(rows);

        RevenueChartResponse response = dashboardService.getRevenueChart(start, end);

        assertEquals(3, response.getLabels().size());
        assertEquals(List.of("01/10", "02/10", "03/10"), response.getLabels());

        // Ngày 1: 0, Ngày 2: 250000, Ngày 3: 0
        assertEquals(BigDecimal.ZERO, response.getData().get(0));
        assertEquals(new BigDecimal("250000.00"), response.getData().get(1));
        assertEquals(BigDecimal.ZERO, response.getData().get(2));

        assertEquals(new BigDecimal("250000.00"), response.getTotalRevenue());
    }

    @Test
    @DisplayName("Revenue Chart: Định dạng nhãn dd/MM/yy khi khoảng ngày lớn hơn 60 ngày")
    void getRevenueChart_shouldFormatLabelsWithYear_whenRangeExceeds60Days() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 20); // > 60 ngày

        when(orderRepository.findDailyRevenueBetween(any(), any())).thenReturn(Collections.emptyList());

        RevenueChartResponse response = dashboardService.getRevenueChart(start, end);

        assertNotNull(response);
        // Nhãn đầu tiên phải có định dạng dd/MM/yy, ví dụ 01/01/26
        assertEquals("01/01/26", response.getLabels().get(0));
    }

    // =========================================================================
    // 4. TOP SELLING PRODUCTS TESTS
    // =========================================================================

    @Test
    @DisplayName("Top Products: Trả về danh sách top sản phẩm với thứ tự, số lượng và doanh thu chính xác")
    void getTopSellingProducts_shouldReturnTopProductsInCorrectOrder() {
        LocalDate start = today.minusDays(6);
        LocalDate end = today;

        List<Object[]> rows = List.of(
                new Object[]{101L, "Cà phê sữa đá", "SP001", 50L, new BigDecimal("1250000.00")},
                new Object[]{102L, "Trà đào cam sả", "SP002", 30L, new BigDecimal("1050000.00")}
        );

        when(orderItemRepository.findTopSellingProductsNative(any(), any(), eq(5))).thenReturn(rows);

        List<TopProductResponse> result = dashboardService.getTopSellingProducts(start, end, 5);

        assertEquals(2, result.size());

        TopProductResponse top1 = result.get(0);
        assertEquals(101L, top1.getProductId());
        assertEquals("Cà phê sữa đá", top1.getProductName());
        assertEquals("SP001", top1.getProductCode());
        assertEquals(50L, top1.getTotalQuantity());
        assertEquals(new BigDecimal("1250000.00"), top1.getTotalRevenue());

        TopProductResponse top2 = result.get(1);
        assertEquals(102L, top2.getProductId());
        assertEquals(30L, top2.getTotalQuantity());
    }

    @Test
    @DisplayName("Top Products: Mặc định limit = 5 khi truyền limit <= 0")
    void getTopSellingProducts_shouldDefaultToLimit5_whenLimitIsZeroOrNegative() {
        when(orderItemRepository.findTopSellingProductsNative(any(), any(), eq(5)))
                .thenReturn(Collections.emptyList());

        List<TopProductResponse> res1 = dashboardService.getTopSellingProducts(today, today, 0);
        List<TopProductResponse> res2 = dashboardService.getTopSellingProducts(today, today, -2);

        assertNotNull(res1);
        assertNotNull(res2);
        verify(orderItemRepository, times(2)).findTopSellingProductsNative(any(), any(), eq(5));
    }

    @Test
    @DisplayName("Top Products: Trả về danh sách rỗng khi không có dữ liệu bán hàng")
    void getTopSellingProducts_shouldReturnEmptyList_whenNoSales() {
        when(orderItemRepository.findTopSellingProductsNative(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<TopProductResponse> result = dashboardService.getTopSellingProducts(today, today, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // 5. LOW STOCK PRODUCTS TESTS
    // =========================================================================

    @Test
    @DisplayName("Low Stock: Lọc đúng các sản phẩm <= threshold và phân loại trạng thái 'Hết hàng' vs 'Sắp hết'")
    void getLowStockProducts_shouldFilterByThreshold_andFormatStatus() {
        Product outOfStockProduct = Product.builder()
                .id(1L).code("SP01").name("Cà phê đen").stockQuantity(0).category(category).build();
        Product lowStockProduct = Product.builder()
                .id(2L).code("SP02").name("Bạc xỉu").stockQuantity(3).category(category).build();

        when(productRepository.findLowStockProducts(5))
                .thenReturn(List.of(outOfStockProduct, lowStockProduct));

        List<LowStockProductResponse> result = dashboardService.getLowStockProducts(5);

        assertEquals(2, result.size());

        assertEquals("Hết hàng", result.get(0).getStatus(), "Stock = 0 phải có trạng thái 'Hết hàng'");
        assertEquals(0, result.get(0).getStockQuantity());
        assertEquals("Cà phê", result.get(0).getCategoryName());

        assertEquals("Sắp hết", result.get(1).getStatus(), "Stock = 3 phải có trạng thái 'Sắp hết'");
        assertEquals(3, result.get(1).getStockQuantity());
    }

    @Test
    @DisplayName("Low Stock: Mặc định threshold = 5 khi threshold truyền vào < 0")
    void getLowStockProducts_shouldDefaultToThreshold5_whenNegative() {
        when(productRepository.findLowStockProducts(5)).thenReturn(Collections.emptyList());

        List<LowStockProductResponse> result = dashboardService.getLowStockProducts(-1);

        assertNotNull(result);
        verify(productRepository).findLowStockProducts(5);
    }
}
