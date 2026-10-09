package com.bizpos;

import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.entity.ProductVariant;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("Integration Tests cho Mô hình SPU - SKU (Product & ProductVariant)")
class ProductVariantIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam Test " + UUID.randomUUID().toString().substring(0, 6))
                .description("Danh mục test biến thể")
                .build());
    }

    @Test
    @DisplayName("1. Tạo 1 Mẫu sản phẩm cha (SPU) và liên kết nhiều Biến thể (SKU: M, L, XL)")
    void createProductWithVariants_shouldPersistHierarchicalHierarchy() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        String styleCode = "POLO-PIMA-" + randomSuffix;

        Product product = Product.builder()
                .name("Áo Polo Pima Cao Cấp " + randomSuffix)
                .code(styleCode)
                .category(testCategory)
                .price(new BigDecimal("350000.00")) // Base reference price
                .material("Cotton Pima 100%")
                .description("Áo polo cao cấp nhiều kích cỡ")
                .stockQuantity(0)
                .build();

        Product savedProduct = productRepository.save(product);

        // Tạo 3 biến thể kích cỡ M, L, XL
        ProductVariant varM = ProductVariant.builder()
                .product(savedProduct)
                .sku(styleCode + "-BLK-M")
                .barcode("8936" + System.currentTimeMillis() % 100000000 + "1")
                .size("M")
                .color("Đen")
                .price(new BigDecimal("350000.00"))
                .costPrice(new BigDecimal("200000.00"))
                .stockQuantity(30)
                .isActive(true)
                .build();

        ProductVariant varL = ProductVariant.builder()
                .product(savedProduct)
                .sku(styleCode + "-BLK-L")
                .barcode("8936" + System.currentTimeMillis() % 100000000 + "2")
                .size("L")
                .color("Đen")
                .price(new BigDecimal("350000.00"))
                .costPrice(new BigDecimal("200000.00"))
                .stockQuantity(25)
                .isActive(true)
                .build();

        // Biến thể XL cho phép ghi đè giá bán cao hơn 20.000đ
        ProductVariant varXL = ProductVariant.builder()
                .product(savedProduct)
                .sku(styleCode + "-BLK-XL")
                .barcode("8936" + System.currentTimeMillis() % 100000000 + "3")
                .size("XL")
                .color("Đen")
                .price(new BigDecimal("370000.00")) // Override price for XL
                .costPrice(new BigDecimal("210000.00"))
                .stockQuantity(15)
                .isActive(true)
                .build();

        variantRepository.saveAll(List.of(varM, varL, varXL));

        // Kiểm tra lưu trữ và truy vấn
        List<ProductVariant> variants = variantRepository.findByProductId(savedProduct.getId());
        assertThat(variants).hasSize(3);

        // Kiểm tra truy vấn theo SKU
        Optional<ProductVariant> foundSku = variantRepository.findBySku(styleCode + "-BLK-XL");
        assertThat(foundSku).isPresent();
        assertThat(foundSku.get().getPrice()).isEqualByComparingTo(new BigDecimal("370000.00"));
        assertThat(foundSku.get().getSize()).isEqualTo("XL");

        // Kiểm tra truy vấn theo ProductId + Size + Color
        Optional<ProductVariant> foundSizeColor = variantRepository.findByProductIdAndSizeAndColor(savedProduct.getId(), "M", "Đen");
        assertThat(foundSizeColor).isPresent();
        assertThat(foundSizeColor.get().getStockQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("2. Khóa bi quan findByIdWithLock trên ProductVariant ngăn ngừa race condition")
    void findByIdWithLock_shouldAcquirePessimisticWriteLock() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = productRepository.save(Product.builder()
                .name("Sơ Mi Test Lock " + randomSuffix)
                .code("SOMI-LOCK-" + randomSuffix)
                .category(testCategory)
                .price(new BigDecimal("400000.00"))
                .stockQuantity(10)
                .build());

        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SOMI-LOCK-" + randomSuffix + "-L")
                .barcode("BC-" + randomSuffix)
                .size("L")
                .color("Trắng")
                .price(new BigDecimal("400000.00"))
                .stockQuantity(20)
                .isActive(true)
                .build());

        Optional<ProductVariant> locked = variantRepository.findByIdWithLock(variant.getId());
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(variant.getId());
    }
}
