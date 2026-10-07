package com.bizpos;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderItem;
import com.bizpos.entity.Product;
import com.bizpos.exception.InsufficientStockException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Order + Inventory trên MySQL")
public class OrderInventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String adminToken;
    private Category testCategory;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");

        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Danh mục Test " + System.currentTimeMillis())
                    .description("Test Category")
                    .build();
            return categoryRepository.save(cat);
        });

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() -> {
            Customer cust = Customer.builder()
                    .fullName("Khách hàng Test " + System.currentTimeMillis())
                    .phone("09" + String.valueOf(System.currentTimeMillis()).substring(5))
                    .email("test" + System.currentTimeMillis() + "@bizpos.vn")
                    .address("TP. Hồ Chí Minh")
                    .build();
            return customerRepository.save(cust);
        });
    }

    @Test
    @DisplayName("Flow thực tế POST /api/orders -> Controller -> Service -> Repository -> MySQL: Order lưu đúng, tồn kho giảm chính xác")
    void createOrder_integration_fullFlow_shouldPersistOrderAndDeductStockInDatabase() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 2 sản phẩm trên MySQL thật
        Product p1 = Product.builder()
                .code("INT_P1_" + suffix)
                .name("Sản phẩm Test 1 " + suffix)
                .price(new BigDecimal("20000.00"))
                .stockQuantity(30)
                .category(testCategory)
                .build();
        p1 = productRepository.save(p1);

        Product p2 = Product.builder()
                .code("INT_P2_" + suffix)
                .name("Sản phẩm Test 2 " + suffix)
                .price(new BigDecimal("50000.00"))
                .stockQuantity(15)
                .category(testCategory)
                .build();
        p2 = productRepository.save(p2);

        // Gửi request POST /api/orders (mua 3 p1 và 2 p2)
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn hàng kiểm thử tích hợp flow thực tế")
                .items(List.of(
                        OrderItemRequest.builder().productId(p1.getId()).quantity(3).build(), // 20,000 * 3 = 60,000
                        OrderItemRequest.builder().productId(p2.getId()).quantity(2).build()  // 50,000 * 2 = 100,000
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        Long orderId = responseJson.get("id").asLong();
        assertNotNull(orderId);

        // 1. Kiểm tra Order tồn tại trong MySQL
        Optional<Order> savedOrderOpt = orderRepository.findByIdWithDetails(orderId);
        assertTrue(savedOrderOpt.isPresent(), "Order phải tồn tại trong cơ sở dữ liệu MySQL");
        Order savedOrder = savedOrderOpt.get();

        // 2. Kiểm tra OrderItem tồn tại và đúng snapshot
        assertEquals(2, savedOrder.getItems().size(), "Đơn hàng phải chứa đúng 2 OrderItem");

        // 3. Kiểm tra Tổng tiền đúng
        assertEquals(new BigDecimal("160000.00"), savedOrder.getTotalAmount(), "Tổng tiền phải bằng 160,000.00");

        // 4. Kiểm tra Stock đã giảm đúng trong MySQL
        Product refreshedP1 = productRepository.findById(p1.getId()).orElseThrow();
        assertEquals(27, refreshedP1.getStockQuantity(), "Stock của p1 phải giảm từ 30 xuống 27 (30 - 3)");

        Product refreshedP2 = productRepository.findById(p2.getId()).orElseThrow();
        assertEquals(13, refreshedP2.getStockQuantity(), "Stock của p2 phải giảm từ 15 xuống 13 (15 - 2)");
    }

    @Test
    @DisplayName("Transaction Rollback: Update Order thất bại do thiếu stock -> rollback toàn bộ, stock cũ không bị lưu ở trạng thái hoàn trả dở dang")
    void updateOrder_integration_transactionRollback_whenStockInsufficient() {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // 1. Tạo sản phẩm với tồn kho ban đầu = 5
        Product p = Product.builder()
                .code("ROLLBACK_" + suffix)
                .name("Sản phẩm Rollback " + suffix)
                .price(new BigDecimal("30000.00"))
                .stockQuantity(5)
                .category(testCategory)
                .build();
        p = productRepository.save(p);

        // 2. Tạo đơn ban đầu mua 2 sản phẩm -> Tồn kho trong DB giảm xuống 3
        CreateOrderRequest initReq = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(OrderItemRequest.builder().productId(p.getId()).quantity(2).build()))
                .build();
        Order initialOrder = orderService.createOrder(initReq);

        Product stockAfterCreate = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(3, stockAfterCreate.getStockQuantity(), "Tồn kho sau khi tạo đơn ban đầu phải là 3 (5 - 2)");

        // 3. Update đơn hàng: yêu cầu số lượng mới là 10.
        // Trong logic updateOrder:
        // - Hoàn trả tồn kho cũ: 3 + 2 = 5
        // - Kiểm tra số lượng mới: 10 > 5 -> ném InsufficientStockException
        CreateOrderRequest updateReq = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(OrderItemRequest.builder().productId(p.getId()).quantity(10).build()))
                .build();

        assertThrows(InsufficientStockException.class, () -> {
            orderService.updateOrder(initialOrder.getId(), updateReq);
        });

        // 4. KIỂM TRA BEHAVIOR CỦA TRANSACTION TRÊN DATABASE THẬT:
        // Dữ liệu cuối cùng trong MySQL PHẢI được rollback:
        // Tồn kho không được ở trạng thái đã hoàn trả (5) mà phải quay lại đúng trạng thái ban đầu là 3!
        Product stockAfterFailedUpdate = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(3, stockAfterFailedUpdate.getStockQuantity(),
                "Transaction phải rollback hoàn trả tồn kho! Tồn kho trong MySQL phải giữ nguyên là 3, không bị thành 5!");

        // Order trong MySQL vẫn giữ nguyên 1 item với số lượng 2 ban đầu
        Order orderAfterFailedUpdate = orderRepository.findByIdWithDetails(initialOrder.getId()).orElseThrow();
        assertEquals(1, orderAfterFailedUpdate.getItems().size());
        assertEquals(2, orderAfterFailedUpdate.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("60000.00"), orderAfterFailedUpdate.getTotalAmount());
    }

    @Test
    @DisplayName("Delete Order -> hoàn trả tồn kho đầy đủ trong MySQL và xóa Order")
    void deleteOrder_integration_shouldRestoreStockAndRemoveOrderInDatabase() {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo sản phẩm có tồn ban đầu = 20
        Product p = Product.builder()
                .code("DEL_RESTORE_" + suffix)
                .name("Sản phẩm Delete " + suffix)
                .price(new BigDecimal("10000.00"))
                .stockQuantity(20)
                .category(testCategory)
                .build();
        p = productRepository.save(p);

        // Tạo đơn mua 6 cái -> tồn còn 14
        CreateOrderRequest req = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(p.getId()).quantity(6).build()))
                .build();
        Order order = orderService.createOrder(req);

        Product stockAfterOrder = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(14, stockAfterOrder.getStockQuantity());

        // Xóa đơn hàng
        orderService.deleteOrder(order.getId());

        // Kiểm tra tồn kho trong MySQL được hoàn trả về 20
        Product stockAfterDelete = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(20, stockAfterDelete.getStockQuantity(), "Tồn kho phải được hoàn trả về 20 trong MySQL sau khi xóa đơn");

        // Kiểm tra Order đã bị xóa khỏi MySQL
        assertTrue(orderRepository.findById(order.getId()).isEmpty(), "Order phải bị xóa khỏi MySQL");
    }
}
