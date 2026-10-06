package com.bizpos.repository;

import com.bizpos.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);

    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    List<Order> findAllByOrderByOrderDateDesc();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    java.math.BigDecimal calculateTotalRevenue();

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o LEFT JOIN FETCH o.customer ORDER BY o.orderDate DESC")
    List<Order> findRecentOrders(org.springframework.data.domain.Pageable pageable);
}
