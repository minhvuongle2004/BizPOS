package com.bizpos.repository;

import com.bizpos.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Khóa dòng sản phẩm bi quan (Pessimistic Write Lock: SELECT ... FOR UPDATE)
     * Ngăn chặn race condition, overselling và lost update khi nhiều transaction cùng trừ kho.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    @Query("SELECT p FROM Product p WHERE " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:productSize IS NULL OR :productSize = '' OR LOWER(p.size) = LOWER(:productSize)) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(p.size) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(p.color) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("productSize") String productSize,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
    long countLowStockProducts(@Param("threshold") Integer threshold);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.stockQuantity <= :threshold ORDER BY p.stockQuantity ASC, p.name ASC")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category ORDER BY p.id ASC")
    List<Product> findAllWithCategory();
}
