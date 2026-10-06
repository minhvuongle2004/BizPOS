package com.bizpos.controller;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.entity.Order;
import com.bizpos.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 1. Tạo mới đơn hàng
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * 2. Lấy danh sách đơn hàng
     * GET /api/orders
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * 3. Xem chi tiết một đơn hàng, gồm cả các OrderItem
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }

    /**
     * 4. Cập nhật đơn hàng theo ID
     * PUT /api/orders/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(
            @PathVariable Long id,
            @RequestBody CreateOrderRequest request) {
        Order updatedOrder = orderService.updateOrder(id, request);
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * 5. Xóa đơn hàng theo ID
     * DELETE /api/orders/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Xóa đơn hàng thành công với ID: " + id);
        return ResponseEntity.ok(response);
    }
}
