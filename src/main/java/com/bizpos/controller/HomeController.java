package com.bizpos.controller;

import com.bizpos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DataSource dataSource;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", "BizPOS");
        model.addAttribute("message", "Hệ thống quản lý bán hàng BizPOS đã khởi động thành công!");

        boolean dbConnected = false;
        String dbInfo = "Không thể kết nối đến cơ sở dữ liệu";

        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                dbConnected = true;
                dbInfo = "Kết nối thành công! Database: " + connection.getCatalog();
            } catch (Exception e) {
                dbInfo = "Lỗi kết nối MySQL: " + e.getMessage();
            }
        }

        // Đọc số lượng bản ghi từ 5 repository
        Map<String, Long> tableCounts = new HashMap<>();
        try {
            tableCounts.put("Categories (Danh mục)", categoryRepository.count());
            tableCounts.put("Products (Sản phẩm)", productRepository.count());
            tableCounts.put("Customers (Khách hàng)", customerRepository.count());
            tableCounts.put("Orders (Đơn hàng)", orderRepository.count());
            tableCounts.put("Order Items (Chi tiết đơn)", orderItemRepository.count());
        } catch (Exception e) {
            model.addAttribute("repoError", "Lỗi đọc dữ liệu từ Repository: " + e.getMessage());
        }

        model.addAttribute("dbConnected", dbConnected);
        model.addAttribute("dbInfo", dbInfo);
        model.addAttribute("tableCounts", tableCounts);

        return "home";
    }
}
