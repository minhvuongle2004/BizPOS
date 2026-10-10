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

    @Query("SELECT sm FROM StockMovement sm WHERE sm.product.id = :productId ORDER BY sm.createdAt ASC, sm.id ASC")
    List<StockMovement> findByProductIdOrderByCreatedAtAsc(@Param("productId") Long productId);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.variant.id = :variantId ORDER BY sm.createdAt ASC, sm.id ASC")
    List<StockMovement> findByVariantIdOrderByCreatedAtAsc(@Param("variantId") Long variantId);

    // =========================================================================
    // KHÓA BẤT BIẾN (APPEND-ONLY): CHẶN TRIỆT ĐỂ MỌI THAO TÁC XÓA BẢN GHI THẺ KHO
    // =========================================================================
    @Override
    default void delete(StockMovement entity) {
        throw new UnsupportedOperationException("Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm thao tác xóa!");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm thao tác xóa!");
    }

    @Override
    default void deleteAll(Iterable<? extends StockMovement> entities) {
        throw new UnsupportedOperationException("Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm thao tác xóa!");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm thao tác xóa!");
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("Sổ thẻ kho là Append-Only (Bất biến), nghiêm cấm thao tác xóa!");
    }
}
