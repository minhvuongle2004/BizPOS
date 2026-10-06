package com.bizpos.controller;

import com.bizpos.entity.Order;
import com.bizpos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final OrderRepository orderRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Lấy danh sách 6 đơn hàng mới nhất để hiển thị ban đầu
        List<Order> recentOrders = orderRepository.findRecentOrders(PageRequest.of(0, 6));
        model.addAttribute("recentOrders", recentOrders);
        return "dashboard";
    }
}
