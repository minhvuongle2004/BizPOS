package com.bizpos.service;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.entity.Order;

import java.util.List;

public interface OrderService {

    /**
     * Tạo đơn hàng mới kết hợp Customer, Product, Order và OrderItem
     */
    Order createOrder(CreateOrderRequest request);

    /**
     * Lấy danh sách toàn bộ đơn hàng (sắp xếp theo ngày tạo mới nhất)
     */
    List<Order> getAllOrders();

    /**
     * Lấy chi tiết đơn hàng theo ID
     */
    Order getOrderById(Long id);

    /**
     * Lấy chi tiết đơn hàng theo mã đơn orderCode
     */
    Order getOrderByCode(String orderCode);

    /**
     * Xóa đơn hàng theo ID
     */
    void deleteOrder(Long id);
}
