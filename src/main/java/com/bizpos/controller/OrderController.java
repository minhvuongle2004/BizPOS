package com.bizpos.controller;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderResponse;
import com.bizpos.entity.Order;
import com.bizpos.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 1. Tạo mới đơn hàng (Áp dụng Idempotency-Key chống tạo đơn lặp khi bấm đúp)
     * POST /api/orders
     */
    @PostMapping
    @com.bizpos.aspect.Idempotent
    public ResponseEntity<OrderResponse> createOrder(@jakarta.validation.Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromEntity(order));
    }

    /**
     * 2. Lấy danh sách đơn hàng
     * GET /api/orders
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> responses = orderService.getAllOrders().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * 3. Xem chi tiết một đơn hàng, gồm cả các OrderItem
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    /**
     * 4. Cập nhật đơn hàng theo ID (sửa thông tin hóa đơn) - Chỉ ADMIN được phép
     * PUT /api/orders/{id}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody CreateOrderRequest request) {
        Order updatedOrder = orderService.updateOrder(id, request);
        return ResponseEntity.ok(OrderResponse.fromEntity(updatedOrder));
    }

    /**
     * 5. Hủy đơn hàng theo ID (hoàn kho an toàn, chống race condition với đổi trả)
     * POST /api/orders/{id}/cancel
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        Order cancelledOrder = orderService.cancelOrder(id, reason);
        return ResponseEntity.ok(OrderResponse.fromEntity(cancelledOrder));
    }

    /**
     * 6. Xóa đơn hàng theo ID
     * DELETE /api/orders/{id}
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Xóa đơn hàng thành công với ID: " + id);
        return ResponseEntity.ok(response);
    }
}
