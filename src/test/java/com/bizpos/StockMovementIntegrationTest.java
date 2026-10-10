package com.bizpos;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.*;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.StockMovementRepository;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.OrderService;
import com.bizpos.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Sổ nhật ký kho (Stock Movement Ledger)")
public class StockMovementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private com.bizpos.service.StockMovementService stockMovementService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String staffToken;
    private Product testProduct;
    private Category testCategory;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin", "ROLE_ADMIN");
        staffToken = "Bearer " + jwtTokenProvider.generateToken("staff", "ROLE_STAFF");

        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Danh mục Test Kho")
                    .description("Test Ledger")
                    .build();
            return categoryRepository.save(cat);
        });

        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        testProduct = Product.builder()
                .code("LEDGER-" + suffix)
                .name("Sản phẩm Sổ Kho " + suffix)
                .price(new BigDecimal("50000"))
                .stockQuantity(100)
                .category(testCategory)
                .build();
        testProduct = productRepository.save(testProduct);

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() -> {
            Customer c = Customer.builder()
                    .fullName("Khách Test Sổ Kho")
                    .phone("098" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 7))
                    .build();
            return customerRepository.save(c);
        });
    }

    @Test
    @DisplayName("1. Bán hàng qua OrderService ghi nhận MovementType.SALE vào Sổ nhật ký kho")
    void testCreateOrder_RecordsSaleMovement() {
        int initialStock = testProduct.getStockQuantity();
        int buyQty = 15;

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn test ghi nhận sổ kho SALE")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(buyQty)
                                .build()
                ))
                .build();

        Order createdOrder = orderService.createOrder(request);

        assertNotNull(createdOrder);
        assertNotNull(createdOrder.getOrderCode());

        // Kiểm tra tồn kho sản phẩm đã bị trừ
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock - buyQty, updatedProduct.getStockQuantity());

        // Kiểm tra sổ nhật ký kho (stock_movements)
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        assertFalse(movements.isEmpty(), "Phải có ít nhất 1 bản ghi biến động kho");

        StockMovement latestMovement = movements.get(0);
        assertEquals(MovementType.SALE, latestMovement.getType());
        assertEquals(buyQty, latestMovement.getQuantity());
        assertEquals(initialStock, latestMovement.getPreviousStock());
        assertEquals(initialStock - buyQty, latestMovement.getCurrentStock());
        assertEquals(createdOrder.getOrderCode(), latestMovement.getReferenceCode());
    }

    @Test
    @DisplayName("2. Điều chỉnh tồn kho thủ công ghi nhận MovementType.ADJUSTMENT vào Sổ nhật ký kho")
    void testUpdateStock_RecordsAdjustmentMovement() {
        int newStock = 120;
        int prevStock = testProduct.getStockQuantity();
        int delta = Math.abs(newStock - prevStock);

        productService.updateStock(testProduct.getId(), newStock);

        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        assertFalse(movements.isEmpty());

        StockMovement latestMovement = movements.get(0);
        assertEquals(MovementType.ADJUSTMENT, latestMovement.getType());
        assertEquals(delta, latestMovement.getQuantity());
        assertEquals(prevStock, latestMovement.getPreviousStock());
        assertEquals(newStock, latestMovement.getCurrentStock());
        assertEquals("ADJUST-" + testProduct.getId(), latestMovement.getReferenceCode());
    }

    @Test
    @DisplayName("3. Hủy đơn hàng ghi nhận MovementType.RETURN vào Sổ nhật ký kho")
    void testDeleteOrder_RecordsReturnMovement() {
        int buyQty = 5;
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(buyQty)
                                .build()
                ))
                .build();

        Order order = orderService.createOrder(request);
        int stockAfterOrder = productRepository.findById(testProduct.getId()).orElseThrow().getStockQuantity();

        // Xóa / Hủy đơn hàng
        orderService.deleteOrder(order.getId());

        int stockAfterDelete = productRepository.findById(testProduct.getId()).orElseThrow().getStockQuantity();
        assertEquals(stockAfterOrder + buyQty, stockAfterDelete);

        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        StockMovement latest = movements.get(0);
        assertEquals(MovementType.RETURN, latest.getType());
        assertEquals(buyQty, latest.getQuantity());
        assertEquals(stockAfterOrder, latest.getPreviousStock());
        assertEquals(stockAfterDelete, latest.getCurrentStock());
        assertEquals(order.getOrderCode(), latest.getReferenceCode());
    }

    @Test
    @DisplayName("4. API GET /api/stock-movements/product/{id} trả về đầy đủ lịch sử thẻ kho")
    void testGetStockMovementsApi() throws Exception {
        // Tạo 1 đơn hàng qua REST API
        String orderJson = """
                {
                    "customerId": %d,
                    "items": [
                        { "productId": %d, "quantity": 10 }
                    ]
                }
                """.formatted(testCustomer.getId(), testProduct.getId());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isCreated());

        // Gọi API lấy thẻ kho của sản phẩm
        mockMvc.perform(get("/api/stock-movements/product/" + testProduct.getId())
                        .header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].typeDescription").value("Xuất bán hàng"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    @DisplayName("5. [RECONCILIATION] Đối soát toán học chuỗi thẻ kho khớp 100% với stockQuantity thực tế")
    void testStockLedgerReconciliation_MatchesCurrentStock() {
        // Chu kỳ nghiệp vụ liên hoàn:
        // Ban đầu: tồn = 100

        // Bước 1: Bán 10 cái (SALE) -> Tồn còn 90
        orderService.createOrder(CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(OrderItemRequest.builder().productId(testProduct.getId()).quantity(10).build()))
                .build());

        // Bước 2: Bán tiếp 25 cái (SALE) -> Tồn còn 65
        Order order2 = orderService.createOrder(CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(OrderItemRequest.builder().productId(testProduct.getId()).quantity(25).build()))
                .build());

        // Bước 3: Hủy đơn order2 (RETURN) -> Tồn hồi phục lên 90
        orderService.deleteOrder(order2.getId());

        // Bước 4: Kiểm kê điều chỉnh lên 110 cái (ADJUSTMENT) -> Tồn là 110
        productService.updateStock(testProduct.getId(), 110);

        // Kiểm tra đối soát toán học qua StockMovementService
        boolean isReconciled = stockMovementService.verifyProductStockReconciliation(testProduct.getId());
        assertTrue(isReconciled, "Chuỗi thẻ kho phải bảo toàn tính toàn vẹn và khớp 100% với tồn kho thực tế");

        // Kiểm tra Product hiện tại
        Product refreshed = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(110, refreshed.getStockQuantity());
    }

    @Test
    @DisplayName("6. [SECURITY - TẦNG REPOSITORY] Chặn đứng toàn bộ phương thức xóa bản ghi thẻ kho")
    void testRepositoryDeleteOperations_AreBlocked() {
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        if (movements.isEmpty()) {
            productService.updateStock(testProduct.getId(), testProduct.getStockQuantity() + 1);
            movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        }

        assertFalse(movements.isEmpty());
        StockMovement sample = movements.get(0);

        assertThrows(UnsupportedOperationException.class, () -> stockMovementRepository.delete(sample));
        assertThrows(UnsupportedOperationException.class, () -> stockMovementRepository.deleteById(sample.getId()));
        assertThrows(UnsupportedOperationException.class, () -> stockMovementRepository.deleteAll());
    }

    @Test
    @DisplayName("7. [SECURITY - TẦNG DATABASE TRIGGER] MySQL Trigger chặn UPDATE và DELETE trực tiếp trên bảng stock_movements")
    void testDatabaseTriggers_PreventDirectUpdateAndDelete() {
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        if (movements.isEmpty()) {
            productService.updateStock(testProduct.getId(), testProduct.getStockQuantity() + 5);
            movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(testProduct.getId());
        }

        assertFalse(movements.isEmpty());
        Long movementId = movements.get(0).getId();

        // 1. Cố tình UPDATE trực tiếp bằng JDBC SQL -> Trigger chặn và ném ngoại lệ
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("UPDATE stock_movements SET quantity = 9999 WHERE id = ?", movementId);
        }, "MySQL Trigger trg_stock_movements_prevent_update phải chặn câu lệnh UPDATE");

        // 2. Cố tình DELETE trực tiếp bằng JDBC SQL -> Trigger chặn và ném ngoại lệ
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("DELETE FROM stock_movements WHERE id = ?", movementId);
        }, "MySQL Trigger trg_stock_movements_prevent_delete phải chặn câu lệnh DELETE");
    }
}
