package com.bizpos.service;

import com.bizpos.dto.StockMovementResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.MovementType;
import com.bizpos.entity.Product;
import com.bizpos.entity.StockMovement;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.StockMovementRepository;
import com.bizpos.service.impl.StockMovementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho StockMovementService (Sổ nhật ký kho / Thẻ kho)")
public class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockMovementServiceImpl stockMovementService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .code("PRD-001")
                .name("Trà sữa trân châu")
                .price(new BigDecimal("35000"))
                .stockQuantity(100)
                .category(Category.builder().id(1L).name("Trà sữa").build())
                .build();
    }

    @Test
    @DisplayName("1. Ghi nhận biến động kho thành công khi bán hàng")
    void testRecordMovement_Success() {
        StockMovement movement = StockMovement.builder()
                .id(10L)
                .product(sampleProduct)
                .type(MovementType.SALE)
                .quantity(5)
                .previousStock(100)
                .currentStock(95)
                .referenceCode("ORD-12345")
                .reason("Xuất kho bán hàng theo đơn ORD-12345")
                .createdBy("admin")
                .build();
        movement.setCreatedAt(LocalDateTime.now());

        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(movement);

        StockMovement saved = stockMovementService.recordMovement(
                sampleProduct,
                MovementType.SALE,
                5,
                100,
                95,
                "ORD-12345",
                "Xuất kho bán hàng theo đơn ORD-12345",
                "admin"
        );

        assertNotNull(saved);
        assertEquals(MovementType.SALE, saved.getType());
        assertEquals(5, saved.getQuantity());
        assertEquals(100, saved.getPreviousStock());
        assertEquals(95, saved.getCurrentStock());
        assertEquals("ORD-12345", saved.getReferenceCode());
        assertEquals("admin", saved.getCreatedBy());

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(captor.capture());
        StockMovement captured = captor.getValue();
        assertEquals(sampleProduct, captured.getProduct());
        assertEquals(5, captured.getQuantity());
    }

    @Test
    @DisplayName("2. Báo lỗi khi recordMovement với Product bị null")
    void testRecordMovement_NullProduct_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                stockMovementService.recordMovement(
                        null,
                        MovementType.SALE,
                        5,
                        10,
                        5,
                        "ORD-001",
                        "Lý do",
                        "admin"
                )
        );
        assertEquals("Sản phẩm không được để trống khi ghi nhật ký kho!", ex.getMessage());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Báo lỗi khi recordMovement với số lượng <= 0")
    void testRecordMovement_InvalidQuantity_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                stockMovementService.recordMovement(
                        sampleProduct,
                        MovementType.SALE,
                        0,
                        10,
                        10,
                        "ORD-001",
                        "Lý do",
                        "admin"
                )
        );
        assertEquals("Số lượng biến động kho phải lớn hơn 0!", ex.getMessage());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Lấy lịch sử biến động kho theo Product ID thành công")
    void testGetMovementsByProductId_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);

        StockMovement m1 = StockMovement.builder()
                .id(1L)
                .product(sampleProduct)
                .type(MovementType.IMPORT)
                .quantity(100)
                .previousStock(0)
                .currentStock(100)
                .referenceCode("INIT-STOCK")
                .reason("Khởi tạo ban đầu")
                .createdBy("admin")
                .build();
        m1.setCreatedAt(LocalDateTime.now().minusDays(1));

        StockMovement m2 = StockMovement.builder()
                .id(2L)
                .product(sampleProduct)
                .type(MovementType.SALE)
                .quantity(5)
                .previousStock(100)
                .currentStock(95)
                .referenceCode("ORD-001")
                .reason("Bán hàng")
                .createdBy("staff")
                .build();
        m2.setCreatedAt(LocalDateTime.now());

        when(stockMovementRepository.findByProductIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m2, m1));

        List<StockMovementResponse> list = stockMovementService.getMovementsByProductId(1L);

        assertEquals(2, list.size());
        assertEquals(MovementType.SALE, list.get(0).getType());
        assertEquals("Xuất bán hàng", list.get(0).getTypeDescription());
        assertEquals(MovementType.IMPORT, list.get(1).getType());
    }

    @Test
    @DisplayName("5. Báo lỗi ResourceNotFound khi lấy biến động của Product ID không tồn tại")
    void testGetMovementsByProductId_NotFound_ThrowsException() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                stockMovementService.getMovementsByProductId(999L)
        );
    }

    @Test
    @DisplayName("6. Phân trang lịch sử biến động kho theo Product ID")
    void testGetMovementsByProductIdPaged_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);

        StockMovement m1 = StockMovement.builder()
                .id(1L)
                .product(sampleProduct)
                .type(MovementType.SALE)
                .quantity(2)
                .previousStock(10)
                .currentStock(8)
                .referenceCode("ORD-100")
                .createdBy("admin")
                .build();
        m1.setCreatedAt(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        when(stockMovementRepository.findByProductId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1)));

        Page<StockMovementResponse> pageResp = stockMovementService.getMovementsByProductId(1L, pageable);

        assertNotNull(pageResp);
        assertEquals(1, pageResp.getTotalElements());
        assertEquals(1, pageResp.getContent().size());
        assertEquals("ORD-100", pageResp.getContent().get(0).getReferenceCode());
    }
}
