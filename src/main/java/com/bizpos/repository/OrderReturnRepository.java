package com.bizpos.repository;

import com.bizpos.entity.OrderReturn;
import org.springframework.data.domain.Page;
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
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    Optional<OrderReturn> findByReturnCode(String returnCode);

    boolean existsByReturnCode(String returnCode);

    @Query("SELECT DISTINCT r FROM OrderReturn r " +
           "LEFT JOIN FETCH r.order o " +
           "LEFT JOIN FETCH o.customer " +
           "WHERE r.id = :id")
    Optional<OrderReturn> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT r FROM OrderReturn r " +
           "LEFT JOIN FETCH r.order o " +
           "LEFT JOIN FETCH o.customer " +
           "WHERE r.returnCode = :returnCode")
    Optional<OrderReturn> findByReturnCodeWithDetails(@Param("returnCode") String returnCode);

    @Query("SELECT DISTINCT r FROM OrderReturn r " +
           "LEFT JOIN FETCH r.order o " +
           "LEFT JOIN FETCH o.customer " +
           "WHERE o.orderCode = :orderCode " +
           "ORDER BY r.createdAt DESC")
    List<OrderReturn> findByOrderOrderCodeWithDetails(@Param("orderCode") String orderCode);

    Page<OrderReturn> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM OrderReturnItem ri " +
           "JOIN ri.orderReturn r " +
           "WHERE r.order.id = :orderId AND ri.product.id = :productId AND r.status = 'COMPLETED'")
    int countReturnedQuantityByOrderAndProduct(@Param("orderId") Long orderId, @Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM OrderReturnItem ri " +
           "JOIN ri.orderReturn r " +
           "WHERE r.order.id = :orderId AND ri.variant.id = :variantId AND r.status = 'COMPLETED'")
    int countReturnedQuantityByOrderAndVariant(@Param("orderId") Long orderId, @Param("variantId") Long variantId);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM OrderReturnItem ri " +
           "JOIN ri.orderReturn r " +
           "WHERE ri.orderItem.id = :orderItemId AND r.status = 'COMPLETED'")
    int countReturnedQuantityByOrderItem(@Param("orderItemId") Long orderItemId);

    @Query("SELECT r FROM OrderReturn r WHERE r.createdAt >= :startDate AND r.createdAt <= :endDate ORDER BY r.createdAt DESC")
    List<OrderReturn> findBetweenDates(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(r.netAmount), 0) FROM OrderReturn r " +
           "WHERE r.status = com.bizpos.enums.ReturnStatus.COMPLETED " +
           "AND r.createdAt >= :startDate AND r.createdAt <= :endDate")
    BigDecimal calculateTotalNetAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(r.totalRefundAmount), 0) FROM OrderReturn r " +
           "WHERE r.status = com.bizpos.enums.ReturnStatus.COMPLETED " +
           "AND r.createdAt >= :startDate AND r.createdAt <= :endDate")
    BigDecimal calculateTotalRefundAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(r.totalExchangeAmount), 0) FROM OrderReturn r " +
           "WHERE r.status = com.bizpos.enums.ReturnStatus.COMPLETED " +
           "AND r.createdAt >= :startDate AND r.createdAt <= :endDate")
    BigDecimal calculateTotalExchangeAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT DATE_FORMAT(r.created_at, '%Y-%m-%d') AS returnDay, COALESCE(SUM(r.net_amount), 0) AS dayNetAmount " +
                   "FROM order_returns r " +
                   "WHERE r.status = 'COMPLETED' AND r.created_at >= :startDate AND r.created_at <= :endDate " +
                   "GROUP BY DATE_FORMAT(r.created_at, '%Y-%m-%d') " +
                   "ORDER BY returnDay ASC", nativeQuery = true)
    List<Object[]> findDailyNetAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
