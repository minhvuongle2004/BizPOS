package com.bizpos;

import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.dto.OrderReturnRequest;
import com.bizpos.dto.ReturnItemRequest;
import com.bizpos.dto.ExchangeItemRequest;
import com.bizpos.enums.ReturnReason;
import com.bizpos.service.OrderService;
import com.bizpos.service.OrderReturnService;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.OrderReturnRepository;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderReturn;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Dashboard Analytics APIs trên MySQL")
public class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderReturnService orderReturnService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private String adminToken;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");

        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Danh mục Test Dashboard " + System.currentTimeMillis())
                    .description("Test Category")
                    .build();
            return categoryRepository.save(cat);
        });
    }

    // =========================================================================
    // 1. GET /api/dashboard/summary
    // =========================================================================

    @Test
    @DisplayName("GET /api/dashboard/summary trả về HTTP 200 và các KPI hợp lệ từ MySQL")
    void getSummary_integration_shouldReturn200AndAccurateKPIs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(json.has("totalRevenue"), "Phải có trường totalRevenue");
        assertTrue(json.has("totalOrders"), "Phải có trường totalOrders");
        assertTrue(json.has("averageOrderValue"), "Phải có trường averageOrderValue");
        assertTrue(json.has("lowStockCount"), "Phải có trường lowStockCount");

        BigDecimal totalRevenue = new BigDecimal(json.get("totalRevenue").asText());
        long totalOrders = json.get("totalOrders").asLong();
        BigDecimal aov = new BigDecimal(json.get("averageOrderValue").asText());

        assertTrue(totalRevenue.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(totalOrders >= 0);

        if (totalOrders > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            assertTrue(aov.compareTo(BigDecimal.ZERO) > 0, "AOV phải > 0 khi có đơn hàng và doanh thu");
        }
    }

    @Test
    @DisplayName("GET /api/dashboard/summary với filter from/to hoạt động chính xác")
    void getSummary_integration_withCustomDateFilter_shouldFilterCorrectly() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        MvcResult result = mockMvc.perform(get("/api/dashboard/summary")
                        .param("startDate", sevenDaysAgo.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertNotNull(json.get("totalRevenue"));
        assertNotNull(json.get("totalOrders"));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary khi khoảng ngày không có đơn hàng nào -> doanh thu = 0, số đơn = 0, AOV = 0")
    void getSummary_integration_emptyRange_shouldReturnZeroRevenueAndOrders() throws Exception {
        // Khoảng ngày quá khứ không có dữ liệu
        MvcResult result = mockMvc.perform(get("/api/dashboard/summary")
                        .param("startDate", "2015-01-01")
                        .param("endDate", "2015-01-02")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(0, new BigDecimal(json.get("totalRevenue").asText()).compareTo(BigDecimal.ZERO));
        assertEquals(0L, json.get("totalOrders").asLong());
        assertEquals(0, new BigDecimal(json.get("averageOrderValue").asText()).compareTo(BigDecimal.ZERO));
    }

    // =========================================================================
    // 2. GET /api/dashboard/revenue
    // =========================================================================

    @Test
    @DisplayName("GET /api/dashboard/revenue trả về HTTP 200, danh sách labels và data đồng bộ độ dài")
    void getRevenueChart_integration_shouldReturn200AndSynchronizedLabelsAndData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/revenue")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-05") // 5 ngày
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(json.has("labels"));
        assertTrue(json.has("data"));
        assertTrue(json.has("totalRevenue"));

        JsonNode labels = json.get("labels");
        JsonNode data = json.get("data");

        assertEquals(5, labels.size(), "Khoảng 5 ngày phải có đúng 5 nhãn thời gian");
        assertEquals(5, data.size(), "Số điểm dữ liệu doanh thu phải bằng đúng số nhãn");
        assertEquals("01/10", labels.get(0).asText());
        assertEquals("05/10", labels.get(4).asText());
    }

    // =========================================================================
    // 3. GET /api/dashboard/top-products
    // =========================================================================

    @Test
    @DisplayName("GET /api/dashboard/top-products trả về HTTP 200 và tuân thủ giới hạn limit")
    void getTopSellingProducts_integration_shouldReturn200AndRespectLimit() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/top-products")
                        .param("limit", "3")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(json.isArray(), "Kết quả phải là một mảng JSON");
        assertTrue(json.size() <= 3, "Số lượng sản phẩm trả về không được vượt quá limit = 3");

        for (JsonNode item : json) {
            assertTrue(item.has("productId"));
            assertTrue(item.has("productName"));
            assertTrue(item.has("totalQuantity"));
            assertTrue(item.has("totalRevenue"));
        }
    }

    // =========================================================================
    // 4. GET /api/dashboard/low-stock
    // =========================================================================

    @Test
    @DisplayName("GET /api/dashboard/low-stock lọc chính xác sản phẩm có stock <= 5, loại bỏ sản phẩm stock > 5")
    void getLowStockProducts_integration_shouldStrictlyFilterProductsAtOrBelowThreshold() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 1 SP có tồn kho = 2 (<= 5)
        Product lowStockProduct = Product.builder()
                .code("LOW_STK_" + suffix)
                .name("SP Thiếu Hàng " + suffix)
                .price(new BigDecimal("15000.00"))
                .stockQuantity(2)
                .category(testCategory)
                .build();
        lowStockProduct = productRepository.save(lowStockProduct);

        // Tạo 1 SP có tồn kho = 80 (> 5)
        Product ampleStockProduct = Product.builder()
                .code("AMPLE_STK_" + suffix)
                .name("SP Nhiều Hàng " + suffix)
                .price(new BigDecimal("25000.00"))
                .stockQuantity(80)
                .category(testCategory)
                .build();
        ampleStockProduct = productRepository.save(ampleStockProduct);

        // Gọi API GET /api/dashboard/low-stock?threshold=5
        MvcResult result = mockMvc.perform(get("/api/dashboard/low-stock")
                        .param("threshold", "5")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(json.isArray());

        boolean foundLowStock = false;
        boolean foundAmpleStock = false;

        for (JsonNode item : json) {
            int stock = item.get("stockQuantity").asInt();
            assertTrue(stock <= 5, "Tất cả sản phẩm trả về phải có stockQuantity <= 5, tìm thấy: " + stock);

            Long id = item.get("id").asLong();
            if (id.equals(lowStockProduct.getId())) {
                foundLowStock = true;
                assertEquals("Sắp hết", item.get("status").asText());
            }
            if (id.equals(ampleStockProduct.getId())) {
                foundAmpleStock = true;
            }
        }

        assertTrue(foundLowStock, "Sản phẩm có tồn kho = 2 phải xuất hiện trong danh sách low-stock");
        assertFalse(foundAmpleStock, "Sản phẩm có tồn kho = 80 tuyệt đối KHÔNG được xuất hiện trong low-stock");
    }

    @Test
    @DisplayName("GET /api/dashboard/low-stock với threshold = 0 chỉ trả về sản phẩm có trạng thái 'Hết hàng'")
    void getLowStockProducts_integration_withThresholdZero_shouldOnlyReturnOutOfStock() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/low-stock")
                        .param("threshold", "0")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(json.isArray());

        for (JsonNode item : json) {
            int stock = item.get("stockQuantity").asInt();
            assertTrue(stock <= 0, "Khi threshold = 0, tất cả sản phẩm phải có stock <= 0");
            assertEquals("Hết hàng", item.get("status").asText());
        }
    }

    @Test
    @DisplayName("Dashboard: Kiểm tra công thức Doanh thu thuần = Σ orders.total_amount + Σ order_returns.net_amount cho cả 4 trường hợp (Δ > 0, Δ = 0, Δ < 0, và trả hàng thuần)")
    void getSummary_netRevenueFormula_withAllExchangeDeltasAndPureReturn() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Lấy chỉ số Dashboard trước khi chạy test
        MvcResult baseResult = mockMvc.perform(get("/api/dashboard/summary")
                        .param("startDate", today.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode baseJson = objectMapper.readTree(baseResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        BigDecimal initialGross = new BigDecimal(baseJson.get("grossRevenue").asText());
        BigDecimal initialNetAdj = new BigDecimal(baseJson.get("netReturnAdjustment").asText());
        BigDecimal initialTotalRevenue = new BigDecimal(baseJson.get("totalRevenue").asText());

        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 4 sản phẩm mẫu
        // SP A (500k) - Dùng cho trả hàng thuần
        Product prodA = productRepository.save(Product.builder()
                .code("NET_A_" + suffix).name("SP A " + suffix).price(new BigDecimal("500000.00")).stockQuantity(10).category(testCategory).build());
        // SP B (300k) - Dùng làm sản phẩm gốc cho 3 ca đổi hàng
        Product prodB = productRepository.save(Product.builder()
                .code("NET_B_" + suffix).name("SP B " + suffix).price(new BigDecimal("300000.00")).stockQuantity(10).category(testCategory).build());
        // SP C (400k) - Dùng để đổi đắt hơn (Δ > 0: 400k - 300k = +100k)
        Product prodC = productRepository.save(Product.builder()
                .code("NET_C_" + suffix).name("SP C " + suffix).price(new BigDecimal("400000.00")).stockQuantity(10).category(testCategory).build());
        // SP D (200k) - Dùng để đổi rẻ hơn (Δ < 0: 200k - 300k = -100k)
        Product prodD = productRepository.save(Product.builder()
                .code("NET_D_" + suffix).name("SP D " + suffix).price(new BigDecimal("200000.00")).stockQuantity(10).category(testCategory).build());
        // SP E (300k) - Dùng để đổi ngang giá (Δ = 0: 300k - 300k = 0k)
        Product prodE = productRepository.save(Product.builder()
                .code("NET_E_" + suffix).name("SP E " + suffix).price(new BigDecimal("300000.00")).stockQuantity(10).category(testCategory).build());

        // 2. Tạo 4 đơn hàng tương ứng
        // Order 1: Mua SP A (500k)
        Order order1 = orderService.createOrder(CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(prodA.getId()).quantity(1).build()))
                .amountPaid(new BigDecimal("500000.00"))
                .build());

        // Order 2: Mua SP B (300k) -> sẽ đổi sang SP C (400k, bù 100k)
        Order order2 = orderService.createOrder(CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .amountPaid(new BigDecimal("300000.00"))
                .build());

        // Order 3: Mua SP B (300k) -> sẽ đổi sang SP E (300k, bù 0k)
        Order order3 = orderService.createOrder(CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .amountPaid(new BigDecimal("300000.00"))
                .build());

        // Order 4: Mua SP B (300k) -> sẽ đổi sang SP D (200k, hoàn 100k)
        Order order4 = orderService.createOrder(CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .amountPaid(new BigDecimal("300000.00"))
                .build());

        // Tổng doanh thu gộp mới thêm: 500k + 300k + 300k + 300k = 1,400,000 đ
        BigDecimal expectedAddedGross = new BigDecimal("1400000.00");

        // 3. Thực hiện 4 ca đổi / trả hàng
        // Case 1: Trả hàng thuần (Order 1, trả SP A) -> Refund = 500k, Exchange = 0k, Net = -500k
        orderReturnService.processReturn(OrderReturnRequest.builder()
                .orderCode(order1.getOrderCode())
                .reason(ReturnReason.DEFECTIVE)
                .returnItems(List.of(ReturnItemRequest.builder().productId(prodA.getId()).quantity(1).build()))
                .build());

        // Case 2: Đổi sang món đắt hơn (Δ > 0: Order 2, trả SP B 300k, lấy SP C 400k) -> Net = +100k
        orderReturnService.processReturn(OrderReturnRequest.builder()
                .orderCode(order2.getOrderCode())
                .reason(ReturnReason.WRONG_SIZE)
                .returnItems(List.of(ReturnItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .exchangeItems(List.of(ExchangeItemRequest.builder().productId(prodC.getId()).quantity(1).build()))
                .build());

        // Case 3: Đổi ngang giá (Δ = 0: Order 3, trả SP B 300k, lấy SP E 300k) -> Net = 0k
        orderReturnService.processReturn(OrderReturnRequest.builder()
                .orderCode(order3.getOrderCode())
                .reason(ReturnReason.COLOR_MISMATCH)
                .returnItems(List.of(ReturnItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .exchangeItems(List.of(ExchangeItemRequest.builder().productId(prodE.getId()).quantity(1).build()))
                .build());

        // Case 4: Đổi sang món rẻ hơn (Δ < 0: Order 4, trả SP B 300k, lấy SP D 200k) -> Net = -100k
        orderReturnService.processReturn(OrderReturnRequest.builder()
                .orderCode(order4.getOrderCode())
                .reason(ReturnReason.CUSTOMER_CHANGE_MIND)
                .returnItems(List.of(ReturnItemRequest.builder().productId(prodB.getId()).quantity(1).build()))
                .exchangeItems(List.of(ExchangeItemRequest.builder().productId(prodD.getId()).quantity(1).build()))
                .build());

        // Tổng điều chỉnh netAmount từ 4 phiếu đổi trả:
        // (-500,000) + (+100,000) + 0 + (-100,000) = -500,000 đ
        BigDecimal expectedAddedNetAdj = new BigDecimal("-500000.00");

        // Doanh thu thuần tăng thêm = Gross (1,400,000) + NetAdj (-500,000) = 900,000 đ
        BigDecimal expectedAddedTotalRevenue = new BigDecimal("900000.00");

        // 4. Gọi lại Dashboard Summary và kiểm tra tính toán
        MvcResult updatedResult = mockMvc.perform(get("/api/dashboard/summary")
                        .param("startDate", today.toString())
                        .param("endDate", today.toString())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode updatedJson = objectMapper.readTree(updatedResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        BigDecimal updatedGross = new BigDecimal(updatedJson.get("grossRevenue").asText());
        BigDecimal updatedNetAdj = new BigDecimal(updatedJson.get("netReturnAdjustment").asText());
        BigDecimal updatedTotalRevenue = new BigDecimal(updatedJson.get("totalRevenue").asText());

        assertEquals(0, initialGross.add(expectedAddedGross).compareTo(updatedGross),
                "Gross Revenue phải tăng thêm đúng 1,400,000 đ");
        assertEquals(0, initialNetAdj.add(expectedAddedNetAdj).compareTo(updatedNetAdj),
                "Net Return Adjustment phải cộng dồn đúng -500,000 đ");
        assertEquals(0, initialTotalRevenue.add(expectedAddedTotalRevenue).compareTo(updatedTotalRevenue),
                "Doanh thu thuần (totalRevenue) phải tăng đúng 900,000 đ (tránh hoàn toàn nguy cơ trừ trùng hay sót Δ < 0)");
        assertEquals(0, updatedGross.add(updatedNetAdj).compareTo(updatedTotalRevenue),
                "Doanh thu thuần luôn bằng chính xác Gross Revenue + Net Return Adjustment");
    }
}
