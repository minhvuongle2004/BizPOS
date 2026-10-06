package com.bizpos.repository;

import com.bizpos.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    @Query("SELECT p FROM Product p WHERE " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= :threshold")
    long countLowStockProducts(@Param("threshold") Integer threshold);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.stockQuantity <= :threshold ORDER BY p.stockQuantity ASC, p.name ASC")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category ORDER BY p.id ASC")
    List<Product> findAllWithCategory();
}
