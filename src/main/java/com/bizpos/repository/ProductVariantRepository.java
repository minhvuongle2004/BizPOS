package com.bizpos.repository;

import com.bizpos.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Khóa dòng biến thể bi quan (Pessimistic Write Lock: SELECT ... FOR UPDATE)
     * Ngăn ngừa race condition và overselling khi bán/hoàn trả biến thể sản phẩm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdWithLock(@Param("id") Long id);

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    List<ProductVariant> findByProductId(Long productId);

    Optional<ProductVariant> findByProductIdAndSizeAndColor(Long productId, String size, String color);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.isActive = true ORDER BY pv.size ASC, pv.color ASC")
    List<ProductVariant> findActiveVariantsByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.stockQuantity <= :threshold AND pv.isActive = true")
    long countLowStockVariants(@Param("threshold") Integer threshold);

    @Query("SELECT pv FROM ProductVariant pv " +
           "JOIN FETCH pv.product p " +
           "WHERE pv.barcode = :barcode")
    Optional<ProductVariant> findByBarcodeWithProduct(@Param("barcode") String barcode);

    @Query("SELECT pv FROM ProductVariant pv " +
           "JOIN FETCH pv.product p " +
           "WHERE pv.isActive = true AND (" +
           "LOWER(pv.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(pv.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
           ") ORDER BY p.name ASC, pv.size ASC")
    List<ProductVariant> searchActiveVariants(@Param("keyword") String keyword);
}
