package com.bizpos.service.impl;

import com.bizpos.dto.DashboardSummaryResponse;
import com.bizpos.dto.LowStockProductResponse;
import com.bizpos.dto.RevenueChartResponse;
import com.bizpos.dto.TopProductResponse;
import com.bizpos.entity.Product;
import com.bizpos.repository.OrderItemRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.OrderReturnRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OrderReturnRepository orderReturnRepository;

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    @Override
    public DashboardSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = normalizeDateRange(startDate, endDate);
        LocalDateTime startDateTime = range[0].atStartOfDay();
        LocalDateTime endDateTime = range[1].atTime(LocalTime.MAX);

        BigDecimal grossRevenue = orderRepository.calculateRevenueBetween(startDateTime, endDateTime);
        if (grossRevenue == null) {
            grossRevenue = BigDecimal.ZERO;
        }

        BigDecimal netReturnAdjustment = orderReturnRepository.calculateTotalNetAmountBetween(startDateTime, endDateTime);
        if (netReturnAdjustment == null) {
            netReturnAdjustment = BigDecimal.ZERO;
        }

        BigDecimal totalRefundAmount = orderReturnRepository.calculateTotalRefundAmountBetween(startDateTime, endDateTime);
        if (totalRefundAmount == null) {
            totalRefundAmount = BigDecimal.ZERO;
        }

        BigDecimal totalExchangeAmount = orderReturnRepository.calculateTotalExchangeAmountBetween(startDateTime, endDateTime);
        if (totalExchangeAmount == null) {
            totalExchangeAmount = BigDecimal.ZERO;
        }

        // Doanh thu thuần = Bán hàng - Hoàn trả (± chênh lệch đổi) = Gross Revenue + netReturnAdjustment
        BigDecimal totalRevenue = grossRevenue.add(netReturnAdjustment);

        long totalOrders = orderRepository.countOrdersBetween(startDateTime, endDateTime);

        BigDecimal averageOrderValue = BigDecimal.ZERO;
        if (totalOrders > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            averageOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP);
        }

        long lowStockCount = productRepository.countLowStockProducts(DEFAULT_LOW_STOCK_THRESHOLD);

        return DashboardSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .lowStockCount(lowStockCount)
                .grossRevenue(grossRevenue)
                .totalRefundAmount(totalRefundAmount)
                .totalExchangeAmount(totalExchangeAmount)
                .netReturnAdjustment(netReturnAdjustment)
                .build();
    }

    @Override
    public RevenueChartResponse getRevenueChart(LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = normalizeDateRange(startDate, endDate);
        LocalDate start = range[0];
        LocalDate end = range[1];

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        // Khởi tạo timeline đầy đủ các ngày trong khoảng thời gian để biểu đồ liền mạch
        Map<String, BigDecimal> dailyMap = new LinkedHashMap<>();
        DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        long daysBetween = ChronoUnit.DAYS.between(start, end);
        DateTimeFormatter displayFormatter = daysBetween > 60
                ? DateTimeFormatter.ofPattern("dd/MM/yy")
                : DateTimeFormatter.ofPattern("dd/MM");

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            dailyMap.put(cur.format(isoFormatter), BigDecimal.ZERO);
            cur = cur.plusDays(1);
        }

        // Lấy dữ liệu bán hàng trực tiếp từ database
        List<Object[]> rows = orderRepository.findDailyRevenueBetween(startDateTime, endDateTime);

        if (rows != null) {
            for (Object[] r : rows) {
                if (r != null && r.length >= 2 && r[0] != null) {
                    String dateKey = r[0].toString();
                    BigDecimal rev = r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO;
                    dailyMap.put(dateKey, rev);
                }
            }
        }

        // Cộng dồn chênh lệch đổi trả theo từng ngày (net_amount: âm nếu trả hoàn tiền, dương nếu khách bù tiền đổi mới)
        List<Object[]> returnRows = orderReturnRepository.findDailyNetAmountBetween(startDateTime, endDateTime);
        if (returnRows != null) {
            for (Object[] r : returnRows) {
                if (r != null && r.length >= 2 && r[0] != null) {
                    String dateKey = r[0].toString();
                    BigDecimal returnNet = r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO;
                    BigDecimal currentDayRev = dailyMap.getOrDefault(dateKey, BigDecimal.ZERO);
                    dailyMap.put(dateKey, currentDayRev.add(returnNet));
                }
            }
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<String> labels = new ArrayList<>();
        List<BigDecimal> data = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : dailyMap.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey(), isoFormatter);
            labels.add(date.format(displayFormatter));
            data.add(entry.getValue());
            totalRevenue = totalRevenue.add(entry.getValue());
        }

        return RevenueChartResponse.builder()
                .labels(labels)
                .data(data)
                .totalRevenue(totalRevenue)
                .build();
    }

    @Override
    public List<TopProductResponse> getTopSellingProducts(LocalDate startDate, LocalDate endDate, int limit) {
        LocalDate[] range = normalizeDateRange(startDate, endDate);
        LocalDateTime startDateTime = range[0].atStartOfDay();
        LocalDateTime endDateTime = range[1].atTime(LocalTime.MAX);
        int finalLimit = limit > 0 ? limit : 5;

        List<Object[]> rows = orderItemRepository.findTopSellingProductsNative(startDateTime, endDateTime, finalLimit);
        List<TopProductResponse> result = new ArrayList<>();

        if (rows != null) {
            for (Object[] r : rows) {
                if (r != null && r.length >= 5) {
                    Long productId = r[0] != null ? ((Number) r[0]).longValue() : null;
                    String productName = r[1] != null ? r[1].toString() : "—";
                    String productCode = r[2] != null ? r[2].toString() : "—";
                    Long totalQuantity = r[3] != null ? ((Number) r[3]).longValue() : 0L;
                    BigDecimal totalRevenue = r[4] != null ? new BigDecimal(r[4].toString()) : BigDecimal.ZERO;

                    result.add(TopProductResponse.builder()
                            .productId(productId)
                            .productName(productName)
                            .productCode(productCode)
                            .totalQuantity(totalQuantity)
                            .totalRevenue(totalRevenue)
                            .build());
                }
            }
        }

        return result;
    }

    @Override
    public List<LowStockProductResponse> getLowStockProducts(int threshold) {
        int finalThreshold = threshold >= 0 ? threshold : DEFAULT_LOW_STOCK_THRESHOLD;
        List<Product> products = productRepository.findLowStockProducts(finalThreshold);
        List<LowStockProductResponse> result = new ArrayList<>();

        for (Product p : products) {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            String status = stock <= 0 ? "Hết hàng" : "Sắp hết";
            String catName = p.getCategory() != null ? p.getCategory().getName() : "—";

            result.add(LowStockProductResponse.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .code(p.getCode())
                    .stockQuantity(stock)
                    .status(status)
                    .categoryName(catName)
                    .build());
        }

        return result;
    }

    /**
     * Chuẩn hóa khoảng thời gian: Mặc định 7 ngày gần nhất nếu không truyền
     */
    private LocalDate[] normalizeDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        if (startDate == null && endDate == null) {
            return new LocalDate[]{today.minusDays(6), today};
        }
        if (startDate != null && endDate == null) {
            return new LocalDate[]{startDate, today};
        }
        if (startDate == null) {
            return new LocalDate[]{endDate.minusDays(6), endDate};
        }
        if (startDate.isAfter(endDate)) {
            return new LocalDate[]{endDate, startDate};
        }
        return new LocalDate[]{startDate, endDate};
    }
}
