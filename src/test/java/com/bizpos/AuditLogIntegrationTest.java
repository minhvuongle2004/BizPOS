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

    @Autowired
    private com.bizpos.repository.UserRepository userRepository;

    @Autowired
    private com.bizpos.security.LoginRateLimiter loginRateLimiter;

    private String adminToken;
    private String staffToken;
    private Product testProduct;
    private Category testCategory;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        loginRateLimiter.clear();
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

    @Test
    @DisplayName("5. Điều chỉnh tồn kho (PATCH /api/products/{id}/stock) kích hoạt ghi nhận ADJUST_STOCK")
    void testAdjustStock_TriggersAuditLog() throws Exception {
        int newStock = 88;
        mockMvc.perform(patch("/api/products/" + testProduct.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .param("quantity", String.valueOf(newStock)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(newStock));

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Product", String.valueOf(testProduct.getId()));
        AuditLog latest = logs.stream()
                .filter(l -> "ADJUST_STOCK".equals(l.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(latest, "Phải ghi nhận bản ghi ADJUST_STOCK trong audit_logs");
        assertEquals("admin", latest.getPerformedBy());
        assertTrue(latest.getDetails().contains("88"));
    }

    @Test
    @DisplayName("6. Đăng nhập thất bại (POST /api/auth/login sai password) ghi nhận LOGIN_FAILED kể cả khi trả về 401 Unauthorized")
    void testLoginFailed_TriggersAuditLog() throws Exception {
        String testUser = "audit_bad_user_" + UUID.randomUUID().toString().substring(0, 5);
        com.bizpos.dto.LoginRequest badLogin = com.bizpos.dto.LoginRequest.builder()
                .username(testUser)
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("User", testUser);
        assertFalse(logs.isEmpty(), "Phải ghi nhận bản ghi LOGIN_FAILED ngay cả khi xác thực thất bại");
        assertEquals("LOGIN_FAILED", logs.get(0).getAction());
        assertEquals("Đăng nhập thất bại", logs.get(0).getActionDescription());
    }

    @Test
    @DisplayName("7. Đổi quyền người dùng (PUT /api/users/{id}/role) ghi nhận CHANGE_ROLE")
    void testChangeUserRole_TriggersAuditLog() throws Exception {
        String testUser = "staff_to_promote_" + UUID.randomUUID().toString().substring(0, 5);
        User user = User.builder()
                .username(testUser)
                .password("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .role(Role.STAFF)
                .build();
        user = userRepository.save(user);

        com.bizpos.dto.UpdateUserRoleRequest req = com.bizpos.dto.UpdateUserRoleRequest.builder()
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(put("/api/users/" + user.getId() + "/role")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("User", String.valueOf(user.getId()));
        AuditLog latest = logs.stream()
                .filter(l -> "CHANGE_ROLE".equals(l.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(latest, "Phải ghi nhận CHANGE_ROLE khi đổi quyền người dùng");
        assertEquals("STAFF", latest.getOldValue());
        assertEquals("ADMIN", latest.getNewValue());
        assertEquals("admin", latest.getPerformedBy());
    }

    @Test
    @DisplayName("8. Nhân viên (STAFF) không có quyền đổi vai trò người dùng (403 Forbidden)")
    void testStaffCannotChangeUserRole() throws Exception {
        com.bizpos.dto.UpdateUserRoleRequest req = com.bizpos.dto.UpdateUserRoleRequest.builder()
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(put("/api/users/1/role")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Rate Limiting đăng nhập thất bại: Sau 5 lần thử sai thì khóa và trả về 429 Too Many Requests, không làm phình bảng Audit Log")
    void testLoginRateLimiting_PreventsAuditLogBloat() throws Exception {
        String victimUser = "victim_user_" + UUID.randomUUID().toString().substring(0, 6);
        com.bizpos.dto.LoginRequest badLogin = com.bizpos.dto.LoginRequest.builder()
                .username(victimUser)
                .password("wrong_password")
                .build();

        String body = objectMapper.writeValueAsString(badLogin);

        // 5 lần thử đầu tiên -> 401 Unauthorized
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        // Đếm số log sau 5 lần thử: Phải là 5 log (4 LOGIN_FAILED và 1 LOGIN_LOCKED)
        List<AuditLog> logsAfter5 = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("User", victimUser);
        assertEquals(5, logsAfter5.size(), "Chính xác 5 bản ghi log cho 5 lần thử");
        assertEquals("LOGIN_LOCKED", logsAfter5.get(0).getAction(), "Lần thử thứ 5 phải ghi nhận LOGIN_LOCKED");

        // Lần thử thứ 6 và thứ 7 -> Bị chặn ngay lập tức với HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tạm thời bị khóa")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        // Khẳng định: Số lượng log trong database KHÔNG BỊ TĂNG THÊM (vẫn giữ nguyên 5, không bị thành 7!)
        List<AuditLog> logsAfterSpam = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("User", victimUser);
        assertEquals(5, logsAfterSpam.size(), "Bảng audit_logs được bảo vệ tuyệt đối khỏi spam brute force!");
    }
}
