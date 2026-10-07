package com.bizpos;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.dto.ProductRequest;
import com.bizpos.entity.*;
import com.bizpos.repository.AuditLogRepository;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Nhật ký kiểm toán hệ thống (Audit Log via Spring AOP)")
public class AuditLogIntegrationTest {

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
    private AuditLogRepository auditLogRepository;

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
                    .name("Danh mục Test Audit")
                    .description("Test Audit Log")
                    .build();
            return categoryRepository.save(cat);
        });

        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        testProduct = Product.builder()
                .code("AUDIT-" + suffix)
                .name("Sản phẩm Audit " + suffix)
                .price(new BigDecimal("100000"))
                .stockQuantity(50)
                .category(testCategory)
                .build();
        testProduct = productRepository.save(testProduct);

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() -> {
            Customer c = Customer.builder()
                    .fullName("Khách Test Audit")
                    .phone("097" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 7))
                    .build();
            return customerRepository.save(c);
        });
    }

    @Test
    @DisplayName("1. Sửa giá sản phẩm (PUT /api/products/{id}) tự động kích hoạt Spring AOP ghi nhận UPDATE_PRICE")
    void testUpdateProductPrice_TriggersAuditLog() throws Exception {
        BigDecimal oldPrice = testProduct.getPrice();
        BigDecimal newPrice = new BigDecimal("85000");

        ProductRequest updateReq = ProductRequest.builder()
                .code(testProduct.getCode())
                .name(testProduct.getName() + " (Đã đổi giá)")
                .price(newPrice)
                .stockQuantity(testProduct.getStockQuantity())
                .categoryId(testCategory.getId())
                .description("Test AOP Audit Price")
                .build();

        mockMvc.perform(put("/api/products/" + testProduct.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Kiểm tra audit log trong database
        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Product", String.valueOf(testProduct.getId()));
        assertFalse(logs.isEmpty(), "Phải có ít nhất 1 bản ghi Audit Log cho sản phẩm");

        AuditLog latest = logs.get(0);
        assertEquals("UPDATE_PRICE", latest.getAction());
        assertEquals("Thay đổi giá bán sản phẩm", latest.getActionDescription());
        assertEquals("admin", latest.getPerformedBy());
        assertTrue(latest.getDetails().contains("85,000"));
    }

    @Test
    @DisplayName("2. Xóa đơn hàng (DELETE /api/orders/{id}) ghi nhận DELETE_ORDER vào Audit Log")
    void testDeleteOrder_TriggersAuditLog() throws Exception {
        CreateOrderRequest orderReq = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn test xóa để ghi audit log")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        Order createdOrder = orderService.createOrder(orderReq);
        String orderCode = createdOrder.getOrderCode();

        // Xóa đơn hàng qua REST API bởi admin
        mockMvc.perform(delete("/api/orders/" + createdOrder.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // Kiểm tra audit log
        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Order", orderCode);
        assertFalse(logs.isEmpty(), "Phải có bản ghi Audit Log DELETE_ORDER");

        AuditLog latest = logs.get(0);
        assertEquals("DELETE_ORDER", latest.getAction());
        assertEquals("admin", latest.getPerformedBy());
        assertTrue(latest.getDetails().contains(orderCode));
    }

    @Test
    @DisplayName("3. Phân quyền: Nhân viên (STAFF) bị chặn 403 Forbidden khi xem /api/audit-logs")
    void testStaffCannotAccessAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Phân quyền: Quản trị viên (ADMIN) truy cập thành công 200 OK /api/audit-logs")
    void testAdminCanAccessAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
