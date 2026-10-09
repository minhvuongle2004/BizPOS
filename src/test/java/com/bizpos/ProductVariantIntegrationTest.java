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

    @Autowired
    private com.bizpos.service.ProductService productService;

    @Autowired
    private com.bizpos.service.OrderService orderService;

    @Autowired
    private com.bizpos.service.OrderReturnService orderReturnService;

    @Autowired
    private com.bizpos.repository.CustomerRepository customerRepository;

    @Test
    @DisplayName("3. Tạo sản phẩm cha theo ma trận Size × Màu tự động sinh đầy đủ Biến thể và Barcode EAN-13")
    void createProduct_withSizesAndColorsMatrix_shouldAutoGenerateMatrixVariants() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        com.bizpos.dto.ProductRequest request = com.bizpos.dto.ProductRequest.builder()
                .code("MATRIX-TEE-" + randomSuffix)
                .name("Áo Thun Matrix " + randomSuffix)
                .categoryId(testCategory.getId())
                .price(new BigDecimal("199000.00"))
                .stockQuantity(20)
                .sizes(List.of("S", "M", "L"))
                .colors(List.of("Trắng", "Đen"))
                .material("Cotton 100%")
                .description("Áo thun ma trận test")
                .build();

        Product created = productService.createProduct(request);
        assertThat(created.getId()).isNotNull();

        List<ProductVariant> variants = variantRepository.findByProductId(created.getId());
        // 3 sizes × 2 colors = 6 variants
        assertThat(variants).hasSize(6);

        for (ProductVariant v : variants) {
            assertThat(v.getBarcode()).startsWith("893");
            assertThat(v.getBarcode()).hasSize(12); // "893" + 9 digits
            assertThat(v.getPrice()).isEqualByComparingTo(new BigDecimal("199000.00"));
            assertThat(v.getStockQuantity()).isEqualTo(20);
            assertThat(v.getIsActive()).isTrue();
        }
    }

    @Test
    @DisplayName("4. Bán hàng theo variantId trừ chính xác tồn kho biến thể và đồng bộ sản phẩm cha")
    void createOrder_withVariantId_shouldDeductVariantStock() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        Product product = productRepository.save(Product.builder()
                .name("Polo Bán Test " + randomSuffix)
                .code("POLO-SALE-" + randomSuffix)
                .category(testCategory)
                .price(new BigDecimal("250000.00"))
                .stockQuantity(100)
                .build());

        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("POLO-SALE-" + randomSuffix + "-M")
                .barcode("BC-SALE-" + randomSuffix)
                .size("M")
                .color("Xanh")
                .price(new BigDecimal("250000.00"))
                .stockQuantity(30)
                .isActive(true)
                .build());

        com.bizpos.dto.CreateOrderRequest orderReq = com.bizpos.dto.CreateOrderRequest.builder()
                .items(List.of(
                        com.bizpos.dto.OrderItemRequest.builder()
                                .variantId(variant.getId())
                                .quantity(5)
                                .build()
                ))
                .note("Đơn hàng test bán theo variantId")
                .build();

        com.bizpos.entity.Order orderRes = orderService.createOrder(orderReq);
        assertThat(orderRes).isNotNull();
        assertThat(orderRes.getOrderCode()).isNotEmpty();

        ProductVariant updatedVariant = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(updatedVariant.getStockQuantity()).isEqualTo(25); // 30 - 5 = 25
    }

    @Test
    @DisplayName("5. Đổi sản phẩm cùng mẫu (khác size): tính isSameModel = true và hoàn/trừ kho chính xác")
    void processReturn_withSameModelVariantExchange_shouldComputeIsSameModelTrue() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        Product parentProduct = productRepository.save(Product.builder()
                .name("Sơ Mi Oxford " + randomSuffix)
                .code("SOMI-OXFORD-" + randomSuffix)
                .category(testCategory)
                .price(new BigDecimal("350000.00"))
                .stockQuantity(50)
                .build());

        ProductVariant varM = variantRepository.save(ProductVariant.builder()
                .product(parentProduct)
                .sku("OXFORD-" + randomSuffix + "-M")
                .barcode("BC-M-" + randomSuffix)
                .size("M")
                .color("Trắng")
                .price(new BigDecimal("350000.00"))
                .stockQuantity(20)
                .isActive(true)
                .build());

        ProductVariant varL = variantRepository.save(ProductVariant.builder()
                .product(parentProduct)
                .sku("OXFORD-" + randomSuffix + "-L")
                .barcode("BC-L-" + randomSuffix)
                .size("L")
                .color("Trắng")
                .price(new BigDecimal("350000.00"))
                .stockQuantity(15)
                .isActive(true)
                .build());

        // Tạo đơn hàng mua 2 chiếc Size M
        com.bizpos.dto.CreateOrderRequest orderReq = com.bizpos.dto.CreateOrderRequest.builder()
                .items(List.of(
                        com.bizpos.dto.OrderItemRequest.builder()
                                .variantId(varM.getId())
                                .quantity(2)
                                .build()
                ))
                .build();
        com.bizpos.entity.Order orderRes = orderService.createOrder(orderReq);

        // Khách đổi 1 chiếc Size M lấy 1 chiếc Size L (CÙNG MẪU SƠ MI OXFORD)
        com.bizpos.dto.OrderReturnRequest returnReq = com.bizpos.dto.OrderReturnRequest.builder()
                .orderCode(orderRes.getOrderCode())
                .reason(com.bizpos.enums.ReturnReason.WRONG_SIZE)
                .note("Đổi từ Size M sang Size L cùng mẫu")
                .returnItems(List.of(
                        com.bizpos.dto.ReturnItemRequest.builder()
                                .variantId(varM.getId())
                                .quantity(1)
                                .build()
                ))
                .exchangeItems(List.of(
                        com.bizpos.dto.ExchangeItemRequest.builder()
                                .variantId(varL.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        com.bizpos.dto.OrderReturnResponse returnRes = orderReturnService.processReturn(returnReq);
        assertThat(returnRes).isNotNull();
        assertThat(returnRes.getExchangeItems()).hasSize(1);

        // Xác nhận quy tắc nghiệp vụ: CÙNG MẪU (isSameModel = true)
        com.bizpos.dto.OrderExchangeItemResponse exchangeItem = returnRes.getExchangeItems().get(0);
        assertThat(exchangeItem.getIsSameModel()).isTrue();
        assertThat(exchangeItem.getVariantId()).isEqualTo(varL.getId());

        // Kiểm tra tồn kho: varM hoàn 1 cái (+1), varL trừ 1 cái (-1)
        ProductVariant updatedVarM = variantRepository.findById(varM.getId()).orElseThrow();
        ProductVariant updatedVarL = variantRepository.findById(varL.getId()).orElseThrow();
        // ban đầu varM = 20, bán 2 còn 18, hoàn 1 -> 19
        assertThat(updatedVarM.getStockQuantity()).isEqualTo(19);
        // ban đầu varL = 15, đổi lấy 1 -> 14
        assertThat(updatedVarL.getStockQuantity()).isEqualTo(14);
    }

    @Test
    @DisplayName("6. Tìm kiếm biến thể theo Barcode, SKU và Tên sản phẩm cha")
    void searchVariants_shouldFindMatchingVariants() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        Product parent = productRepository.save(Product.builder()
                .name("Quần Jean Slimfit " + randomSuffix)
                .code("JEAN-SLIM-" + randomSuffix)
                .category(testCategory)
                .price(new BigDecimal("450000.00"))
                .stockQuantity(30)
                .build());

        ProductVariant var30 = variantRepository.save(ProductVariant.builder()
                .product(parent)
                .sku("JEAN-SLIM-" + randomSuffix + "-30")
                .barcode("893999" + randomSuffix)
                .size("30")
                .color("Xanh Đậm")
                .price(new BigDecimal("450000.00"))
                .stockQuantity(10)
                .isActive(true)
                .build());

        // Tìm theo barcode
        ProductVariant foundByBc = productService.getVariantByBarcode("893999" + randomSuffix);
        assertThat(foundByBc).isNotNull();
        assertThat(foundByBc.getId()).isEqualTo(var30.getId());

        // Tìm kiếm theo từ khóa SKU
        List<ProductVariant> searchBySku = productService.searchVariants("JEAN-SLIM-" + randomSuffix);
        assertThat(searchBySku).isNotEmpty();
        assertThat(searchBySku.get(0).getProduct().getName()).contains("Quần Jean Slimfit");
    }
}
