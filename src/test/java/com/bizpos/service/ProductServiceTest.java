package com.bizpos.service;

import com.bizpos.dto.PageResponse;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.ProductResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.OrderItemRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho ProductService")
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private StockMovementService stockMovementService;

    @InjectMocks
    private ProductServiceImpl productService;

    private Category sampleCategory;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleCategory = Category.builder()
                .id(1L)
                .name("Đồ uống")
                .description("Các loại nước giải khát")
                .build();

        sampleProduct = Product.builder()
                .id(10L)
                .code("SP001")
                .name("Cà phê sữa đá")
                .price(new BigDecimal("25000.00"))
                .stockQuantity(50)
                .category(sampleCategory)
                .description("Cà phê pha phin truyền thống")
                .build();
    }

    // =========================================================================
    // 1. TẠO SẢN PHẨM (CREATE PRODUCT)
    // =========================================================================

    @Test
    @DisplayName("Tạo sản phẩm thành công khi dữ liệu hợp lệ")
    void createProduct_shouldSucceed_whenDataIsValid() {
        ProductRequest request = ProductRequest.builder()
                .code("  SP002  ")
                .name("  Trà đào cam sả  ")
                .price(new BigDecimal("35000.00"))
                .stockQuantity(20)
                .categoryId(1L)
                .description("Trà tươi ngon")
                .build();

        when(productRepository.existsByCode("SP002")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        Product created = productService.createProduct(request);

        assertNotNull(created);
        assertEquals("SP002", created.getCode(), "Mã sản phẩm phải được trim()");
        assertEquals("Trà đào cam sả", created.getName(), "Tên sản phẩm phải được trim()");
        assertEquals(new BigDecimal("35000.00"), created.getPrice());
        assertEquals(20, created.getStockQuantity());
        assertEquals(sampleCategory, created.getCategory());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm gán mặc định stockQuantity = 0 khi request stockQuantity là null")
    void createProduct_shouldDefaultStockToZero_whenStockQuantityIsNull() {
        ProductRequest request = ProductRequest.builder()
                .code("SP003")
                .name("Nước suối")
                .price(new BigDecimal("10000.00"))
                .stockQuantity(null)
                .categoryId(1L)
                .build();

        when(productRepository.existsByCode("SP003")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.createProduct(request);

        assertNotNull(created);
        assertEquals(0, created.getStockQuantity(), "StockQuantity phải mặc định là 0 nếu null");
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi mã sản phẩm đã tồn tại -> ném DuplicateResourceException")
    void createProduct_shouldThrowDuplicateResourceException_whenCodeAlreadyExists() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001")
                .name("Cà phê sữa mới")
                .price(new BigDecimal("30000.00"))
                .categoryId(1L)
                .build();

        when(productRepository.existsByCode("SP001")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(
                DuplicateResourceException.class,
                () -> productService.createProduct(request)
        );

        assertTrue(ex.getMessage().contains("SP001"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi giá bán <= 0 -> ném IllegalArgumentException")
    void createProduct_shouldThrowIllegalArgumentException_whenPriceIsNegativeOrZero() {
        ProductRequest requestZeroPrice = ProductRequest.builder()
                .code("SP004")
                .name("Bánh mì")
                .price(BigDecimal.ZERO)
                .categoryId(1L)
                .build();

        ProductRequest requestNegativePrice = ProductRequest.builder()
                .code("SP005")
                .name("Bánh bao")
                .price(new BigDecimal("-15000.00"))
                .categoryId(1L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(requestZeroPrice));
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(requestNegativePrice));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi danh mục không tồn tại -> ném ResourceNotFoundException")
    void createProduct_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        ProductRequest request = ProductRequest.builder()
                .code("SP006")
                .name("Trà sen vàng")
                .price(new BigDecimal("45000.00"))
                .categoryId(999L)
                .build();

        when(productRepository.existsByCode("SP006")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi mã hoặc tên sản phẩm rỗng -> ném IllegalArgumentException")
    void createProduct_shouldThrowIllegalArgumentException_whenCodeOrNameIsEmpty() {
        ProductRequest emptyCodeReq = ProductRequest.builder()
                .code("   ")
                .name("Sản phẩm A")
                .price(new BigDecimal("10000.00"))
                .categoryId(1L)
                .build();

        ProductRequest emptyNameReq = ProductRequest.builder()
                .code("SP007")
                .name("")
                .price(new BigDecimal("10000.00"))
                .categoryId(1L)
                .build();

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(emptyCodeReq));
        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(emptyNameReq));
    }

    // =========================================================================
    // 2. CẬP NHẬT SẢN PHẨM (UPDATE PRODUCT)
    // =========================================================================

    @Test
    @DisplayName("Cập nhật thông tin sản phẩm thành công")
    void updateProduct_shouldSucceed_whenDataIsValid() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001-NEW")
                .name("Cà phê sữa đá đặc biệt")
                .price(new BigDecimal("28000.00"))
                .stockQuantity(100)
                .categoryId(1L)
                .description("Hương vị đậm đà hơn")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.existsByCodeAndIdNot("SP001-NEW", 10L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(10L, request);

        assertEquals("SP001-NEW", updated.getCode());
        assertEquals("Cà phê sữa đá đặc biệt", updated.getName());
        assertEquals(new BigDecimal("28000.00"), updated.getPrice());
        assertEquals(100, updated.getStockQuantity());
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("Cập nhật sản phẩm giữ nguyên mã cũ thì không bị coi là trùng mã")
    void updateProduct_shouldAllowKeepingSameCode() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001") // Giữ nguyên mã SP001
                .name("Cà phê sữa đá (Đổi tên)")
                .price(new BigDecimal("26000.00"))
                .categoryId(1L)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(10L, request);

        assertEquals("SP001", updated.getCode());
        assertEquals("Cà phê sữa đá (Đổi tên)", updated.getName());
        verify(productRepository, never()).existsByCodeAndIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thất bại khi mã mới bị trùng với sản phẩm khác -> ném DuplicateResourceException")
    void updateProduct_shouldThrowDuplicateResourceException_whenNewCodeExistsForAnotherProduct() {
        ProductRequest request = ProductRequest.builder()
                .code("SP999")
                .name("Cà phê đen")
                .price(new BigDecimal("20000.00"))
                .categoryId(1L)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.existsByCodeAndIdNot("SP999", 10L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> productService.updateProduct(10L, request));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thất bại khi ID không tồn tại -> ném ResourceNotFoundException")
    void updateProduct_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001")
                .name("Tên bất kỳ")
                .price(new BigDecimal("20000.00"))
                .categoryId(1L)
                .build();

        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(999L, request));
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thất bại khi danh mục mới không tồn tại -> ném ResourceNotFoundException")
    void updateProduct_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001")
                .name("Tên bất kỳ")
                .price(new BigDecimal("20000.00"))
                .categoryId(999L)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(10L, request));
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thất bại khi giá bán <= 0 -> ném IllegalArgumentException")
    void updateProduct_shouldThrowIllegalArgumentException_whenPriceIsInvalid() {
        ProductRequest request = ProductRequest.builder()
                .code("SP001")
                .name("Tên bất kỳ")
                .price(new BigDecimal("-1000.00"))
                .categoryId(1L)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(10L, request));
    }

    // =========================================================================
    // 3. CẬP NHẬT TỒN KHO (UPDATE STOCK)
    // =========================================================================

    @Test
    @DisplayName("Cập nhật tồn kho thành công khi số lượng >= 0")
    void updateStock_shouldSucceed_whenQuantityIsValid() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateStock(10L, 80);

        assertEquals(80, updated.getStockQuantity());
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("Cập nhật tồn kho thất bại khi số lượng < 0 hoặc null -> ném IllegalArgumentException")
    void updateStock_shouldThrowIllegalArgumentException_whenQuantityIsNegativeOrNull() {
        assertThrows(IllegalArgumentException.class, () -> productService.updateStock(10L, -5));
        assertThrows(IllegalArgumentException.class, () -> productService.updateStock(10L, null));
        verify(productRepository, never()).save(any(Product.class));
    }

    // =========================================================================
    // 4. XÓA SẢN PHẨM (DELETE PRODUCT)
    // =========================================================================

    @Test
    @DisplayName("Xóa sản phẩm thành công khi sản phẩm chưa từng có trong đơn hàng")
    void deleteProduct_shouldSucceed_whenProductHasNoOrderItems() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderItemRepository.existsByProductId(10L)).thenReturn(false);

        productService.deleteProduct(10L);

        verify(productRepository).delete(sampleProduct);
    }

    @Test
    @DisplayName("Xóa sản phẩm thất bại khi sản phẩm không tồn tại -> ném ResourceNotFoundException")
    void deleteProduct_shouldThrowResourceNotFoundException_whenProductDoesNotExist() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(999L));
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    @DisplayName("Xóa sản phẩm thất bại khi sản phẩm đã có trong chi tiết đơn hàng -> ném IllegalStateException (409)")
    void deleteProduct_shouldThrowIllegalStateException_whenProductIsUsedInOrderItems() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderItemRepository.existsByProductId(10L)).thenReturn(true);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> productService.deleteProduct(10L)
        );

        assertTrue(ex.getMessage().contains("đã tồn tại trong đơn hàng"));
        verify(productRepository, never()).delete(any(Product.class));
    }
}
