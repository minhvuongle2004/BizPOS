package com.bizpos.repository;

import com.bizpos.entity.MovementType;
import com.bizpos.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.product WHERE sm.product.id = :productId ORDER BY sm.createdAt DESC")
    List<StockMovement> findByProductIdOrderByCreatedAtDesc(@Param("productId") Long productId);

    @Query(value = "SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.product WHERE sm.product.id = :productId ORDER BY sm.createdAt DESC",
           countQuery = "SELECT COUNT(sm) FROM StockMovement sm WHERE sm.product.id = :productId")
    Page<StockMovement> findByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query(value = "SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.product ORDER BY sm.createdAt DESC",
           countQuery = "SELECT COUNT(sm) FROM StockMovement sm")
    Page<StockMovement> findAllWithProduct(Pageable pageable);

    @Query(value = "SELECT sm FROM StockMovement sm LEFT JOIN FETCH sm.product WHERE " +
           "(:productId IS NULL OR sm.product.id = :productId) AND " +
           "(:type IS NULL OR sm.type = :type) AND " +
           "(:from IS NULL OR sm.createdAt >= :from) AND " +
           "(:to IS NULL OR sm.createdAt <= :to) " +
           "ORDER BY sm.createdAt DESC",
           countQuery = "SELECT COUNT(sm) FROM StockMovement sm WHERE " +
           "(:productId IS NULL OR sm.product.id = :productId) AND " +
           "(:type IS NULL OR sm.type = :type) AND " +
           "(:from IS NULL OR sm.createdAt >= :from) AND " +
           "(:to IS NULL OR sm.createdAt <= :to)")
    Page<StockMovement> filterMovements(
            @Param("productId") Long productId,
            @Param("type") MovementType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
