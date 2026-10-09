package com.bizpos.service;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderItem;
import com.bizpos.entity.Product;
import com.bizpos.exception.InsufficientStockException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho OrderService và Inventory Business Logic")
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private StockMovementService stockMovementService;

    @Mock
    private com.bizpos.repository.ProductVariantRepository productVariantRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Customer sampleCustomer;
    private Product productA;
    private Product productB;
    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Đồ uống")
                .description("Các loại nước giải khát")
                .build();

        sampleCustomer = Customer.builder()
                .id(1L)
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .email("nguyenvana@gmail.com")
                .address("123 Lê Lợi, TP.HCM")
                .build();

        productA = Product.builder()
                .id(101L)
                .code("SP001")
                .name("Cà phê sữa đá")
                .price(new BigDecimal("25000.00"))
                .stockQuantity(50)
                .category(category)
                .build();

        productB = Product.builder()
                .id(102L)
                .code("SP002")
                .name("Trà đào cam sả")
                .price(new BigDecimal("35000.00"))
                .stockQuantity(20)
                .category(category)
                .build();
    }

    // =========================================================================
    // 1. TẠO ORDER (CREATE ORDER)
    // =========================================================================

    @Test
    @DisplayName("Tạo order thành công với một sản phẩm")
    void createOrder_shouldSucceed_withSingleProduct() {
        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .note("Giao nhanh trước 12h")
                .items(List.of(
                        OrderItemRequest.builder().productId(101L).quantity(2).build()
                ))
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order createdOrder = orderService.createOrder(request);

        // Assert
        assertNotNull(createdOrder);
        assertEquals(sampleCustomer, createdOrder.getCustomer());
        assertEquals("Giao nhanh trước 12h", createdOrder.getNote());
        assertNotNull(createdOrder.getOrderDate());
        assertNotNull(createdOrder.getOrderCode());
        assertTrue(createdOrder.getOrderCode().startsWith("ORD-"));

        // Kiểm tra danh sách item
        assertEquals(1, createdOrder.getItems().size());
        OrderItem item = createdOrder.getItems().get(0);
        assertEquals(productA, item.getProduct());
        assertEquals("Cà phê sữa đá", item.getProductName());
        assertEquals(new BigDecimal("25000.00"), item.getUnitPrice());
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("50000.00"), item.getLineTotal());

        // Tổng tiền đơn hàng: 25,000 * 2 = 50,000
        assertEquals(new BigDecimal("50000.00"), createdOrder.getTotalAmount());

        // Kiểm tra tồn kho đã giảm: 50 - 2 = 48
        assertEquals(48, productA.getStockQuantity());
        verify(productRepository).save(productA);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Tạo order thành công với nhiều sản phẩm")
    void createOrder_shouldSucceed_withMultipleProducts() {
        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(
                        OrderItemRequest.builder().productId(101L).quantity(3).build(), // 25,000 * 3 = 75,000
                        OrderItemRequest.builder().productId(102L).quantity(2).build()  // 35,000 * 2 = 70,000
                ))
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.findByIdWithLock(102L)).thenReturn(Optional.of(productB));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order createdOrder = orderService.createOrder(request);

        // Assert
        assertEquals(2, createdOrder.getItems().size());
        // Tổng tiền: 75,000 + 70,000 = 145,000
        assertEquals(new BigDecimal("145000.00"), createdOrder.getTotalAmount());

        // Kiểm tra tồn kho của cả 2 sản phẩm
        assertEquals(47, productA.getStockQuantity()); // 50 - 3
        assertEquals(18, productB.getStockQuantity()); // 20 - 2
        verify(productRepository).save(productA);
        verify(productRepository).save(productB);
    }

    @Test
    @DisplayName("Giá sản phẩm phải được lấy từ database, không tin giá từ request")
    void createOrder_shouldUseProductPriceFromDatabase_notFromRequest() {
        // Arrange: Product A có giá 25,000 trong DB
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(4).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order createdOrder = orderService.createOrder(request);

        // Assert: Đơn giá và thành tiền tính theo giá DB (25,000 * 4 = 100,000)
        OrderItem item = createdOrder.getItems().get(0);
        assertEquals(productA.getPrice(), item.getUnitPrice());
        assertEquals(new BigDecimal("100000.00"), item.getLineTotal());
        assertEquals(new BigDecimal("100000.00"), createdOrder.getTotalAmount());
    }

    @Test
    @DisplayName("OrderItem lưu snapshot tên sản phẩm và đơn giá tại thời điểm đặt hàng")
    void createOrder_shouldSaveSnapshotOfProductNameAndUnitPrice_inOrderItem() {
        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(1).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order createdOrder = orderService.createOrder(request);

        // Assert: OrderItem snapshot đúng productName và unitPrice
        OrderItem item = createdOrder.getItems().get(0);
        assertEquals("Cà phê sữa đá", item.getProductName());
        assertEquals(new BigDecimal("25000.00"), item.getUnitPrice());

        // Giả sử sau này Product trong DB bị đổi tên hoặc đổi giá
        productA.setName("Cà phê sữa đá - Giá mới 2026");
        productA.setPrice(new BigDecimal("30000.00"));

        // Snapshot trong OrderItem đã tạo vẫn phải giữ nguyên giá trị ban đầu
        assertEquals("Cà phê sữa đá", item.getProductName());
        assertEquals(new BigDecimal("25000.00"), item.getUnitPrice());
    }

    @Test
    @DisplayName("Tổng tiền được tính chính xác với nhiều sản phẩm có đơn giá lẻ")
    void createOrder_shouldCalculateTotalAmountAccurately() {
        // Arrange: Sản phẩm có đơn giá lẻ
        Product productOdd1 = Product.builder()
                .id(201L).name("Bánh ngọt").price(new BigDecimal("18500.00")).stockQuantity(10).build();
        Product productOdd2 = Product.builder()
                .id(202L).name("Khăn lạnh").price(new BigDecimal("2500.00")).stockQuantity(50).build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder().productId(201L).quantity(3).build(), // 18,500 * 3 = 55,500
                        OrderItemRequest.builder().productId(202L).quantity(7).build()  // 2,500 * 7 = 17,500
                ))
                .build();

        when(productRepository.findByIdWithLock(201L)).thenReturn(Optional.of(productOdd1));
        when(productRepository.findByIdWithLock(202L)).thenReturn(Optional.of(productOdd2));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order createdOrder = orderService.createOrder(request);

        // Assert: 55,500 + 17,500 = 73,000
        assertEquals(new BigDecimal("73000.00"), createdOrder.getTotalAmount());
    }

    @Test
    @DisplayName("Order được tạo đúng khách hàng khi có customerId")
    void createOrder_shouldAssignCorrectCustomer_whenCustomerIdProvided() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(1).build()))
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order createdOrder = orderService.createOrder(request);

        assertNotNull(createdOrder.getCustomer());
        assertEquals(1L, createdOrder.getCustomer().getId());
        assertEquals("Nguyễn Văn A", createdOrder.getCustomer().getFullName());
        verify(customerRepository).findById(1L);
    }

    @Test
    @DisplayName("Order được tạo cho khách vãng lai khi customerId = null")
    void createOrder_shouldAllowWalkInCustomer_whenCustomerIdIsNull() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(null) // Khách vãng lai
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(1).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order createdOrder = orderService.createOrder(request);

        assertNull(createdOrder.getCustomer());
        verify(customerRepository, never()).findById(any());
    }

    // =========================================================================
    // 2. INVENTORY KHI TẠO ORDER
    // =========================================================================

    @Test
    @DisplayName("Đặt hàng thành công -> stock giảm đúng số lượng quantity")
    void createOrder_shouldDeductStock_whenStockIsSufficient() {
        productA.setStockQuantity(15);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(5).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(request);

        assertEquals(10, productA.getStockQuantity());
        verify(productRepository).save(productA);
    }

    @Test
    @DisplayName("Đủ stock (mua đúng bằng số lượng tồn kho) -> order được tạo, stock về 0")
    void createOrder_shouldAllowBuyingExactStockQuantity() {
        productA.setStockQuantity(7);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(7).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder(request);

        assertNotNull(order);
        assertEquals(0, productA.getStockQuantity());
        assertTrue(productA.getStockQuantity() >= 0, "Stock không được âm");
    }

    @Test
    @DisplayName("Không đủ stock -> ném InsufficientStockException")
    void createOrder_shouldThrowInsufficientStockException_whenStockIsInsufficient() {
        productA.setStockQuantity(3);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(4).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request)
        );

        assertTrue(ex.getMessage().contains("không đủ số lượng tồn kho"));
        assertTrue(ex.getMessage().contains("Tồn kho hiện tại: 3"));
        assertTrue(ex.getMessage().contains("yêu cầu: 4"));
    }

    @Test
    @DisplayName("Khi không đủ stock -> không được tạo order thành công (không gọi orderRepository.save)")
    void createOrder_shouldNotSaveOrder_whenStockIsInsufficient() {
        productA.setStockQuantity(2);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(10).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request));

        // OrderRepository.save tuyệt đối không được gọi
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Stock không bị âm khi hàng tồn kho = 0 và có yêu cầu mua")
    void createOrder_shouldEnsureStockCannotBecomeNegative() {
        productA.setStockQuantity(0);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(1).build()))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request));

        // Tồn kho vẫn giữ nguyên = 0, không bị âm
        assertEquals(0, productA.getStockQuantity());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Khi đơn có nhiều sản phẩm và sản phẩm thứ hai không đủ stock -> dừng tạo order")
    void createOrder_shouldFail_whenAnyItemHasInsufficientStock() {
        productA.setStockQuantity(50); // Đủ
        productB.setStockQuantity(1);  // Không đủ cho 5
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder().productId(101L).quantity(2).build(),
                        OrderItemRequest.builder().productId(102L).quantity(5).build()
                ))
                .build();

        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.findByIdWithLock(102L)).thenReturn(Optional.of(productB));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request));

        verify(orderRepository, never()).save(any(Order.class));
    }

    // =========================================================================
    // 3. CẬP NHẬT ORDER (UPDATE ORDER)
    // =========================================================================

    @Test
    @DisplayName("Update quantity thành công")
    void updateOrder_shouldUpdateQuantitySuccessfully() {
        // Arrange: Order cũ có Product A với số lượng 2
        Order existingOrder = new Order();
        existingOrder.setId(10L);
        existingOrder.setOrderCode("ORD-20261007-001");
        existingOrder.setCustomer(sampleCustomer);

        OrderItem oldItem = OrderItem.builder()
                .product(productA)
                .productName(productA.getName())
                .unitPrice(productA.getPrice())
                .quantity(2)
                .lineTotal(new BigDecimal("50000.00"))
                .build();
        existingOrder.addItem(oldItem);
        existingOrder.setTotalAmount(new BigDecimal("50000.00"));

        // Stock Product A hiện tại sau khi đã trừ 2 trước đó là 48
        productA.setStockQuantity(48);

        // Request: cập nhật Product A lên số lượng 5
        CreateOrderRequest updateReq = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(5).build()))
                .build();

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order updatedOrder = orderService.updateOrder(10L, updateReq);

        // Assert
        assertNotNull(updatedOrder);
        assertEquals(1, updatedOrder.getItems().size());
        assertEquals(5, updatedOrder.getItems().get(0).getQuantity());
        // Tổng tiền mới: 25,000 * 5 = 125,000
        assertEquals(new BigDecimal("125000.00"), updatedOrder.getTotalAmount());

        // Stock: ban đầu 48 + 2 (hoàn trả) = 50, sau đó 50 - 5 = 45
        assertEquals(45, productA.getStockQuantity());
        verify(orderRepository).save(existingOrder);
    }

    @Test
    @DisplayName("Khi update order, stock của các item cũ được hoàn trả trước rồi mới trừ theo quantity mới")
    void updateOrder_shouldRestoreOldItemsStock_thenDeductNewQuantity() {
        // Arrange: Đơn cũ có Product A số lượng 5. Stock hiện tại trong kho chỉ còn 1.
        // Nếu không hoàn trả trước, yêu cầu mua mới 6 sẽ bị báo thiếu hàng (vì 6 > 1).
        // Nhưng nếu hoàn trả trước: 1 + 5 = 6, mua 6 là vừa đủ!
        Order existingOrder = new Order();
        existingOrder.setId(10L);
        OrderItem oldItem = OrderItem.builder()
                .product(productA)
                .productName(productA.getName())
                .unitPrice(productA.getPrice())
                .quantity(5)
                .lineTotal(new BigDecimal("125000.00"))
                .build();
        existingOrder.addItem(oldItem);

        productA.setStockQuantity(1);

        CreateOrderRequest updateReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(6).build()))
                .build();

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order updatedOrder = orderService.updateOrder(10L, updateReq);

        // Assert
        assertNotNull(updatedOrder);
        // Tồn kho cuối cùng: (1 + 5) - 6 = 0
        assertEquals(0, productA.getStockQuantity());
    }

    @Test
    @DisplayName("Update sang sản phẩm khác hoạt động đúng: hoàn trả sản phẩm cũ và trừ tồn sản phẩm mới")
    void updateOrder_shouldAllowSwitchingToDifferentProduct_whenStockIsSufficient() {
        // Arrange: Đơn cũ mua Product A (số lượng 3, stock hiện tại là 10).
        Order existingOrder = new Order();
        existingOrder.setId(10L);
        OrderItem oldItem = OrderItem.builder()
                .product(productA)
                .productName(productA.getName())
                .unitPrice(productA.getPrice())
                .quantity(3)
                .lineTotal(new BigDecimal("75000.00"))
                .build();
        existingOrder.addItem(oldItem);

        productA.setStockQuantity(10);
        productB.setStockQuantity(20);

        // Request: chuyển sang mua Product B (số lượng 4, giá 35,000)
        CreateOrderRequest updateReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(102L).quantity(4).build()))
                .build();

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));
        when(productRepository.findByIdWithLock(102L)).thenReturn(Optional.of(productB));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order updatedOrder = orderService.updateOrder(10L, updateReq);

        // Assert
        assertEquals(1, updatedOrder.getItems().size());
        assertEquals("Trà đào cam sả", updatedOrder.getItems().get(0).getProductName());
        assertEquals(new BigDecimal("140000.00"), updatedOrder.getTotalAmount()); // 35,000 * 4

        // Product A được hoàn lại 3: 10 + 3 = 13
        assertEquals(13, productA.getStockQuantity());
        // Product B bị trừ 4: 20 - 4 = 16
        assertEquals(16, productB.getStockQuantity());
    }

    @Test
    @DisplayName("Update với quantity vượt stock -> reject bằng InsufficientStockException")
    void updateOrder_shouldThrowInsufficientStockException_whenNewQuantityExceedsStock() {
        // Arrange: Đơn cũ mua Product A số lượng 2. Stock hiện tại = 3.
        // Sau khi hoàn trả: 3 + 2 = 5.
        // Yêu cầu mới: mua 10 -> Không đủ (10 > 5)
        Order existingOrder = new Order();
        existingOrder.setId(10L);
        OrderItem oldItem = OrderItem.builder()
                .product(productA)
                .productName(productA.getName())
                .unitPrice(productA.getPrice())
                .quantity(2)
                .lineTotal(new BigDecimal("50000.00"))
                .build();
        existingOrder.addItem(oldItem);

        productA.setStockQuantity(3);

        CreateOrderRequest updateReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(10).build()))
                .build();

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));
        when(productRepository.findByIdWithLock(101L)).thenReturn(Optional.of(productA));

        // Act & Assert
        assertThrows(InsufficientStockException.class, () -> orderService.updateOrder(10L, updateReq));

        // verify orderRepository.save không được gọi khi bị reject
        verify(orderRepository, never()).save(any(Order.class));
    }

    // =========================================================================
    // 4. XÓA ORDER (DELETE ORDER)
    // =========================================================================

    @Test
    @DisplayName("Delete order thành công khi order tồn tại")
    void deleteOrder_shouldSucceed_whenOrderExists() {
        Order existingOrder = new Order();
        existingOrder.setId(10L);

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));

        orderService.deleteOrder(10L);

        verify(orderRepository).delete(existingOrder);
    }

    @Test
    @DisplayName("Delete order -> hoàn trả stock đúng quantity cho tất cả sản phẩm trong đơn")
    void deleteOrder_shouldRestoreStock_whenOrderIsDeleted() {
        // Arrange: Đơn hàng chứa Product A (qty 3) và Product B (qty 2)
        Order existingOrder = new Order();
        existingOrder.setId(10L);

        productA.setStockQuantity(10);
        productB.setStockQuantity(5);

        OrderItem item1 = OrderItem.builder().product(productA).quantity(3).build();
        OrderItem item2 = OrderItem.builder().product(productB).quantity(2).build();
        existingOrder.addItem(item1);
        existingOrder.addItem(item2);

        when(orderRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(existingOrder));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        orderService.deleteOrder(10L);

        // Assert: Stock Product A tăng từ 10 lên 13
        assertEquals(13, productA.getStockQuantity());
        // Stock Product B tăng từ 5 lên 7
        assertEquals(7, productB.getStockQuantity());

        verify(productRepository).save(productA);
        verify(productRepository).save(productB);
        verify(orderRepository).delete(existingOrder);
    }

    @Test
    @DisplayName("Delete order ném ResourceNotFoundException khi order không tồn tại")
    void deleteOrder_shouldThrowResourceNotFoundException_whenOrderNotFound() {
        when(orderRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.deleteOrder(999L));

        verify(orderRepository, never()).delete(any(Order.class));
    }

    // =========================================================================
    // 5. VALIDATION CƠ BẢN (EDGE CASES)
    // =========================================================================

    @Test
    @DisplayName("Tạo đơn hàng ném IllegalArgumentException khi request rỗng hoặc không có items")
    void createOrder_shouldThrowIllegalArgumentException_whenItemsIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(null));

        CreateOrderRequest emptyReq = CreateOrderRequest.builder().items(Collections.emptyList()).build();
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(emptyReq));
    }

    @Test
    @DisplayName("Tạo đơn hàng ném ResourceNotFoundException khi không tìm thấy customer")
    void createOrder_shouldThrowResourceNotFoundException_whenCustomerNotFound() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(999L)
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(1).build()))
                .build();

        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng ném ResourceNotFoundException khi không tìm thấy product")
    void createOrder_shouldThrowResourceNotFoundException_whenProductNotFound() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(999L).quantity(1).build()))
                .build();

        when(productRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng ném IllegalArgumentException khi quantity <= 0")
    void createOrder_shouldThrowIllegalArgumentException_whenQuantityIsInvalid() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(101L).quantity(0).build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }
}
