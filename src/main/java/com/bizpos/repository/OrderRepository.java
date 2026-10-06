package com.bizpos.repository;

import com.bizpos.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    List<Order> findAllByOrderByOrderDateDesc();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer ORDER BY o.orderDate DESC")
    List<Order> findRecentOrders(Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderDate >= :startDateTime AND o.orderDate <= :endDateTime")
    BigDecimal calculateRevenueBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderDate >= :startDateTime AND o.orderDate <= :endDateTime")
    long countOrdersBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query(value = "SELECT DATE_FORMAT(o.order_date, '%Y-%m-%d') AS orderDay, COALESCE(SUM(o.total_amount), 0) AS dayRevenue " +
                   "FROM orders o " +
                   "WHERE o.order_date >= :startDateTime AND o.order_date <= :endDateTime " +
                   "GROUP BY DATE_FORMAT(o.order_date, '%Y-%m-%d') " +
                   "ORDER BY orderDay ASC", nativeQuery = true)
    List<Object[]> findDailyRevenueBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);
}
