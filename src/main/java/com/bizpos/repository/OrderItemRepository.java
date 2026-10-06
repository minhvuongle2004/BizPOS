package com.bizpos.repository;

import com.bizpos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    boolean existsByProductId(Long productId);

    @Query(value = "SELECT p.id AS productId, " +
                   "oi.product_name AS productName, " +
                   "p.code AS productCode, " +
                   "COALESCE(SUM(oi.quantity), 0) AS totalQuantity, " +
                   "COALESCE(SUM(oi.line_total), 0) AS totalRevenue " +
                   "FROM order_items oi " +
                   "JOIN products p ON oi.product_id = p.id " +
                   "JOIN orders o ON oi.order_id = o.id " +
                   "WHERE o.order_date >= :startDateTime AND o.order_date <= :endDateTime " +
                   "GROUP BY p.id, oi.product_name, p.code " +
                   "ORDER BY totalQuantity DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopSellingProductsNative(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("limit") int limit);
}
