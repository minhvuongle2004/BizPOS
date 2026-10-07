package com.bizpos;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.LoginRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.RegisterRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Order;
import com.bizpos.entity.Product;
import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.UserRepository;
import com.bizpos.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Integration Tests cho Security & Authentication trên MySQL")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        // Đảm bảo có sẵn tài khoản admin và staff
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .build());
        }

        if (userRepository.findByUsername("staff").isEmpty()) {
            userRepository.save(User.builder()
                    .username("staff")
                    .password(passwordEncoder.encode("staff123"))
                    .role(Role.STAFF)
                    .build());
        }

        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");
        staffToken = jwtTokenProvider.generateToken("staff", "STAFF");
    }

    // =========================================================================
    // 1. AUTHENTICATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Login: Đúng username và password trả về HTTP 200 và JWT token hợp lệ")
    void login_withCorrectCredentials_returns200AndJwtToken() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("admin123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(json.has("token"));
        assertNotNull(json.get("token").asText());
        assertFalse(json.get("token").asText().isEmpty());
        assertEquals("admin", json.get("username").asText());
        assertEquals("ADMIN", json.get("role").asText());
        assertEquals("Bearer", json.get("type").asText());

        // Validate token nhận được
        assertTrue(jwtTokenProvider.validateToken(json.get("token").asText()));
    }

    @Test
    @DisplayName("Login: Sai mật khẩu trả về HTTP 401 Unauthorized")
    void login_withWrongPassword_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("admin")
                .password("wrong_password_999")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(401, json.get("status").asInt());
        assertTrue(json.get("message").asText().contains("không chính xác"));
    }

    @Test
    @DisplayName("Login: Username không tồn tại trả về HTTP 401 Unauthorized")
    void login_withNonExistentUser_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("ghost_user_" + System.currentTimeMillis())
                .password("some_password")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(401, json.get("status").asInt());
    }

    @Test
    @DisplayName("Register: Đăng ký tài khoản hợp lệ trả về HTTP 201 Created và JWT token")
    void register_withValidData_returns201AndToken() throws Exception {
        String uniqueUser = "new_staff_" + (System.currentTimeMillis() % 100000);
        RegisterRequest request = RegisterRequest.builder()
                .username(uniqueUser)
                .password("password123")
                .role(Role.STAFF)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(json.has("token"));
        assertEquals(uniqueUser, json.get("username").asText());
        assertEquals("STAFF", json.get("role").asText());
    }

    @Test
    @DisplayName("Public endpoints: /api/auth/login và /api/auth/register không yêu cầu JWT Token")
    void publicEndpoints_doNotRequireJwt() throws Exception {
        // Gửi request không có token header tới login với body rỗng -> 400 Bad Request (validation), KHÔNG PHẢI 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Gửi request không có token header tới register với body rỗng -> 400 Bad Request (validation), KHÔNG PHẢI 401
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 2. JWT PROTECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("JWT Protection: Gọi protected API không có Authorization header trả về HTTP 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(401, json.get("status").asInt());
    }

    @Test
    @DisplayName("JWT Protection: Gọi protected API với token sai/không hợp lệ trả về HTTP 401")
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer invalid.garbage.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Protection: Gọi protected API với token đã hết hạn trả về HTTP 401")
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                "dGVzdF9qd3Rfc2VjcmV0X2tleV9mb3JfZGV2ZWxvcG1lbnRfcHVycG9zZXNfb25seV8yNTZiaXRzX2xvbmc=",
                -5000L
        );
        String expiredToken = expiredProvider.generateToken("admin", "ADMIN");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT Protection: Gọi protected API với Bearer token hợp lệ truy cập thành công HTTP 200")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. ROLE-BASED ACCESS CONTROL (ADMIN VS STAFF)
    // =========================================================================

    @Test
    @DisplayName("Role ADMIN: Được phép thực hiện thao tác DELETE tài nguyên")
    void admin_canPerformDelete_returns200() throws Exception {
        // Tạo một danh mục tạm thời không có sản phẩm để xóa
        Category tempCat = categoryRepository.save(Category.builder()
                .name("Danh mục test xóa ADMIN " + System.currentTimeMillis())
                .description("Mô tả test")
                .build());

        mockMvc.perform(delete("/api/categories/" + tempCat.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Role STAFF: Bị từ chối khi thực hiện thao tác DELETE trả về HTTP 403 Forbidden")
    void staff_cannotPerformDelete_returns403() throws Exception {
        // Tạo một danh mục tạm thời
        Category tempCat = categoryRepository.save(Category.builder()
                .name("Danh mục test xóa STAFF " + System.currentTimeMillis())
                .description("Mô tả test")
                .build());

        MvcResult result = mockMvc.perform(delete("/api/categories/" + tempCat.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, json.get("status").asInt());
        assertTrue(json.get("message").asText().contains("không có quyền") ||
                json.get("message").asText().contains("ADMIN"));
    }

    @Test
    @DisplayName("Role STAFF: Được phép truy cập các API nghiệp vụ thông thường (GET/POST/PUT)")
    void staff_canAccessBusinessEndpoints_returns200() throws Exception {
        // GET /api/products
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // GET /api/categories
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // GET /api/customers
        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // GET /api/dashboard/summary
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Excel Export: ADMIN được phép xuất Excel trả về HTTP 200")
    void excelExport_adminAllowed_returns200() throws Exception {
        mockMvc.perform(get("/api/products/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Excel Export: STAFF bị từ chối trả về HTTP 403 Forbidden")
    void excelExport_staffForbidden_returns403() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/export")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, json.get("status").asInt());
    }

    @Test
    @DisplayName("Excel Export: Không có token trả về HTTP 401 Unauthorized")
    void excelExport_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/products/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Excel Import: STAFF bị từ chối trả về HTTP 403 Forbidden")
    void excelImport_staffForbidden_returns403() throws Exception {
        MockMultipartFile dummyFile = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        MvcResult result = mockMvc.perform(multipart("/api/products/import")
                        .file(dummyFile)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, json.get("status").asInt());
    }

    @Test
    @DisplayName("Excel Import: Không có token trả về HTTP 401 Unauthorized")
    void excelImport_withoutToken_returns401() throws Exception {
        MockMultipartFile dummyFile = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/products/import")
                        .file(dummyFile))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Product Update: STAFF bị từ chối sửa sản phẩm/đổi giá trả về HTTP 403 Forbidden")
    void staff_cannotUpdateProduct_returns403() throws Exception {
        Category cat = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.builder().name("Cat Update Test " + System.currentTimeMillis()).build()));
        Product p = productRepository.save(Product.builder()
                .code("PRD_STF_" + System.currentTimeMillis())
                .name("Sản phẩm gốc")
                .price(new BigDecimal("50000"))
                .stockQuantity(10)
                .category(cat)
                .build());

        ProductRequest updateReq = ProductRequest.builder()
                .code(p.getCode())
                .name("Sản phẩm đổi tên")
                .price(new BigDecimal("10000")) // Hạ giá
                .stockQuantity(10)
                .categoryId(cat.getId())
                .build();

        MvcResult result = mockMvc.perform(put("/api/products/" + p.getId())
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, json.get("status").asInt());
    }

    @Test
    @DisplayName("Product Update: ADMIN được phép sửa sản phẩm/đổi giá trả về HTTP 200 OK")
    void admin_canUpdateProduct_returns200() throws Exception {
        Category cat = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.builder().name("Cat Update Test " + System.currentTimeMillis()).build()));
        Product p = productRepository.save(Product.builder()
                .code("PRD_ADM_" + System.currentTimeMillis())
                .name("Sản phẩm gốc")
                .price(new BigDecimal("50000"))
                .stockQuantity(10)
                .category(cat)
                .build());

        ProductRequest updateReq = ProductRequest.builder()
                .code(p.getCode())
                .name("Sản phẩm Admin đổi giá")
                .price(new BigDecimal("60000"))
                .stockQuantity(15)
                .categoryId(cat.getId())
                .build();

        mockMvc.perform(put("/api/products/" + p.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Order Update: STAFF bị từ chối sửa thông tin hóa đơn trả về HTTP 403 Forbidden")
    void staff_cannotUpdateOrder_returns403() throws Exception {
        Order order = orderRepository.findAll().stream().findFirst().orElse(null);
        if (order != null && !order.getItems().isEmpty()) {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .items(List.of(OrderItemRequest.builder()
                            .productId(order.getItems().get(0).getProduct().getId())
                            .quantity(1)
                            .build()))
                    .build();

            MvcResult result = mockMvc.perform(put("/api/orders/" + order.getId())
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andReturn();

            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertEquals(403, json.get("status").asInt());
        }
    }

    @Test
    @DisplayName("Order Update: ADMIN được phép sửa thông tin hóa đơn trả về HTTP 200 OK")
    void admin_canUpdateOrder_returns200() throws Exception {
        Order order = orderRepository.findAll().stream().findFirst().orElse(null);
        if (order != null && !order.getItems().isEmpty()) {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .items(List.of(OrderItemRequest.builder()
                            .productId(order.getItems().get(0).getProduct().getId())
                            .quantity(1)
                            .build()))
                    .build();

            mockMvc.perform(put("/api/orders/" + order.getId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }
}
