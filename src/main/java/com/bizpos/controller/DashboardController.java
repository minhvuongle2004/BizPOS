package com.bizpos.controller;

import com.bizpos.entity.Order;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // 1. Thống kê số lượng tổng quan từ Database thật
        long totalProducts = productRepository.count();
        long totalCategories = categoryRepository.count();
        long totalCustomers = customerRepository.count();
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenue();

        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("totalCategories", totalCategories);
        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        // 2. Danh sách đơn hàng gần đây (Top 6 đơn mới nhất)
        List<Order> recentOrders = orderRepository.findRecentOrders(PageRequest.of(0, 6));
        model.addAttribute("recentOrders", recentOrders);

        // 3. Dữ liệu biểu đồ doanh thu 7 ngày gần nhất
        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("dd/MM");
        Map<LocalDate, BigDecimal> dailyRevenueMap = new LinkedHashMap<>();

        // Khởi tạo timeline 7 ngày gần nhất
        for (int i = 6; i >= 0; i--) {
            dailyRevenueMap.put(today.minusDays(i), BigDecimal.ZERO);
        }

        // Lấy tất cả đơn hàng để tính tổng doanh thu theo ngày
        List<Order> allOrders = orderRepository.findAll();
        for (Order order : allOrders) {
            if (order.getOrderDate() != null) {
                LocalDate orderDay = order.getOrderDate().toLocalDate();
                if (dailyRevenueMap.containsKey(orderDay)) {
                    BigDecimal currentAmount = dailyRevenueMap.get(orderDay);
                    BigDecimal orderAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
                    dailyRevenueMap.put(orderDay, currentAmount.add(orderAmount));
                }
            }
        }

        List<String> chartLabels = new ArrayList<>();
        List<BigDecimal> chartData = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : dailyRevenueMap.entrySet()) {
            chartLabels.add(entry.getKey().format(labelFormatter));
            chartData.add(entry.getValue());
        }

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);

        return "dashboard";
    }
}
