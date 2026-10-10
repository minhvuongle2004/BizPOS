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
     * Cập nhật đơn hàng theo ID (cập nhật khách hàng, ghi chú, và thay thế danh sách sản phẩm)
     */
    Order updateOrder(Long id, CreateOrderRequest request);

    /**
     * Hủy đơn hàng theo ID (hoàn trả tồn kho cho các sản phẩm chưa bị đổi/trả)
     */
    Order cancelOrder(Long id, String reason);

    /**
     * Xóa đơn hàng theo ID
     */
    void deleteOrder(Long id);
}
