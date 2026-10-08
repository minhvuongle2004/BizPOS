package com.bizpos;

import com.bizpos.dto.CategoryRequest;
import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.CustomerRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.dto.ProductRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.Product;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.OrderService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Product, Category, Customer API trên MySQL")
public class MasterDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderService orderService;

    private String adminToken;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");

        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Danh mục Test MD " + System.currentTimeMillis())
                    .description("Test Category")
                    .build();
            return categoryRepository.save(cat);
        });
    }

    // =========================================================================
    // 1. PRODUCT INTEGRATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Product: Tạo sản phẩm trùng code trả về HTTP 409 Conflict")
    void createProduct_shouldReturn409_whenDuplicateCode() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String code = "MD_P_" + suffix;

        Product p = Product.builder()
                .code(code)
                .name("Sản phẩm gốc")
                .price(new BigDecimal("20000.00"))
                .stockQuantity(10)
                .category(testCategory)
                .build();
        productRepository.save(p);

        ProductRequest duplicateReq = ProductRequest.builder()
                .code(code)
                .name("Sản phẩm trùng code")
                .price(new BigDecimal("25000.00"))
                .categoryId(testCategory.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Product: Giá bán <= 0 hoặc stockQuantity < 0 trả về HTTP 400 Bad Request")
    void createProduct_shouldReturn400_whenInvalidPriceOrStock() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Giá âm
        ProductRequest negPriceReq = ProductRequest.builder()
                .code("NEG_PR_" + suffix)
                .name("Sản phẩm giá âm")
                .price(new BigDecimal("-10000.00"))
                .categoryId(testCategory.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negPriceReq)))
                .andExpect(status().isBadRequest());

        // Tồn kho âm
        ProductRequest negStockReq = ProductRequest.builder()
                .code("NEG_STK_" + suffix)
                .name("Sản phẩm tồn âm")
                .price(new BigDecimal("15000.00"))
                .stockQuantity(-5)
                .categoryId(testCategory.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negStockReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Product: Tạo với categoryId không tồn tại trả về HTTP 404 Not Found")
    void createProduct_shouldReturn404_whenCategoryNotFound() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        ProductRequest req = ProductRequest.builder()
                .code("NO_CAT_" + suffix)
                .name("Sản phẩm không có danh mục")
                .price(new BigDecimal("20000.00"))
                .categoryId(999999L)
                .build();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Product: Truy vấn sản phẩm không tồn tại trả về HTTP 404 Not Found")
    void getProduct_shouldReturn404_whenProductNotFound() throws Exception {
        mockMvc.perform(get("/api/products/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Product: Xóa sản phẩm đang nằm trong đơn hàng trả về HTTP 409 Conflict")
    void deleteProduct_shouldReturn409_whenProductIsUsedInOrder() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // 1. Tạo sản phẩm
        Product product = Product.builder()
                .code("IN_ORDER_" + suffix)
                .name("Sản phẩm có trong đơn")
                .price(new BigDecimal("30000.00"))
                .stockQuantity(10)
                .category(testCategory)
                .build();
        product = productRepository.save(product);

        // 2. Tạo đơn hàng chứa sản phẩm này
        CreateOrderRequest orderReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(product.getId()).quantity(1).build()))
                .build();
        orderService.createOrder(orderReq);

        // 3. Thử xóa sản phẩm -> Phải trả về HTTP 409 Conflict do ràng buộc nghiệp vụ
        mockMvc.perform(delete("/api/products/" + product.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 2. CATEGORY INTEGRATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Category: Tạo danh mục trùng tên trả về HTTP 409 Conflict")
    void createCategory_shouldReturn409_whenDuplicateName() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String catName = "Danh Mục Dup " + suffix;

        Category cat = Category.builder().name(catName).build();
        categoryRepository.save(cat);

        CategoryRequest dupReq = CategoryRequest.builder().name(catName).build();

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Category: Truy vấn danh mục không tồn tại trả về HTTP 404 Not Found")
    void getCategory_shouldReturn404_whenCategoryNotFound() throws Exception {
        mockMvc.perform(get("/api/categories/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Category: Xóa danh mục đang có sản phẩm liên kết trả về HTTP 409 Conflict")
    void deleteCategory_shouldReturn409_whenCategoryHasProducts() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // 1. Tạo danh mục
        Category cat = Category.builder()
                .name("Danh mục có SP " + suffix)
                .description("Có sản phẩm liên kết")
                .build();
        cat = categoryRepository.save(cat);

        // 2. Tạo sản phẩm liên kết với danh mục này
        Product p = Product.builder()
                .code("LINKED_SP_" + suffix)
                .name("Sản phẩm thuộc danh mục")
                .price(new BigDecimal("20000.00"))
                .stockQuantity(5)
                .category(cat)
                .build();
        productRepository.save(p);

        // 3. Thử xóa danh mục -> Phải trả về HTTP 409 Conflict
        mockMvc.perform(delete("/api/categories/" + cat.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // 3. CUSTOMER INTEGRATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Customer: Tạo khách hàng trùng SĐT hoặc trùng Email trả về HTTP 409 Conflict")
    void createCustomer_shouldReturn409_whenDuplicatePhoneOrEmail() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String phone = "09" + suffix;
        String email = "cust_" + suffix + "@gmail.com";

        Customer c = Customer.builder()
                .fullName("Khách Hàng Gốc")
                .phone(phone)
                .email(email)
                .build();
        customerRepository.save(c);

        // Trùng SĐT
        CustomerRequest dupPhoneReq = CustomerRequest.builder()
                .fullName("Khách Trùng SĐT")
                .phone(phone)
                .email("khac_" + suffix + "@gmail.com")
                .build();

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupPhoneReq)))
                .andExpect(status().isConflict());

        // Trùng Email
        CustomerRequest dupEmailReq = CustomerRequest.builder()
                .fullName("Khách Trùng Email")
                .phone("08" + suffix)
                .email(email)
                .build();

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupEmailReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Customer: Truy vấn khách hàng không tồn tại trả về HTTP 404 Not Found")
    void getCustomer_shouldReturn404_whenCustomerNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Product: Tạo sản phẩm thời trang đầy đủ Size, Color, Material và lọc theo Size")
    void createProduct_andFilterBySize_shouldWorkAccurately() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 1 sản phẩm Size XL
        ProductRequest reqXL = ProductRequest.builder()
                .code("TSHIRT_XL_" + suffix)
                .name("Áo Thun Oversize XL")
                .size("XL")
                .color("Đen")
                .material("Cotton 100%")
                .price(new BigDecimal("220000.00"))
                .stockQuantity(25)
                .categoryId(testCategory.getId())
                .build();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqXL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value("XL"))
                .andExpect(jsonPath("$.color").value("Đen"))
                .andExpect(jsonPath("$.material").value("Cotton 100%"));

        // Lọc theo productSize=XL
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("productSize", "XL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code == 'TSHIRT_XL_" + suffix + "')].size").value("XL"));
    }
}
