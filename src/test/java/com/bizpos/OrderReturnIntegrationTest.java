package com.bizpos;

import com.bizpos.dto.*;
import com.bizpos.entity.*;
import com.bizpos.enums.ReturnReason;
import com.bizpos.enums.ReturnType;
import com.bizpos.repository.*;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.OrderReturnService;
import com.bizpos.service.OrderService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Quy trình Đổi - Trả hàng Thời trang (Return & Exchange Engine)")
public class OrderReturnIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    private String adminToken;
    private Product productPoloM;
    private Product productPoloL;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");

        Category category = categoryRepository.findByName("Áo Thời Trang")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Áo Thời Trang").build()));

        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 2 sản phẩm thời trang để test (Polo M và Polo L)
        productPoloM = productRepository.save(Product.builder()
                .code("RET_POLO_M_" + suffix)
                .name("Áo Polo Test M")
                .size("M")
                .color("Đen")
                .material("Cotton")
                .price(new BigDecimal("300000.00"))
                .stockQuantity(50)
                .category(category)
                .build());

        productPoloL = productRepository.save(Product.builder()
                .code("RET_POLO_L_" + suffix)
                .name("Áo Polo Test L")
                .size("L")
                .color("Đen")
                .material("Cotton")
                .price(new BigDecimal("320000.00"))
                .stockQuantity(30)
                .category(category)
                .build());

        // Tạo đơn hàng mua 2 chiếc Polo M
        CreateOrderRequest orderReq = CreateOrderRequest.builder()
                .note("Đơn hàng test đổi trả")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(productPoloM.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        testOrder = orderService.createOrder(orderReq);
    }

    @Test
    @DisplayName("1. Tra cứu điều kiện đổi trả: Đơn hàng mới mua đủ điều kiện (7 ngày) và đúng số lượng")
    void getEligibleReturnInfo_shouldReturnCorrectQuantities() throws Exception {
        mockMvc.perform(get("/api/returns/eligible/" + testOrder.getOrderCode())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value(testOrder.getOrderCode()))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.items[0].productId").value(productPoloM.getId()))
                .andExpect(jsonPath("$.items[0].purchasedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].alreadyReturnedQuantity").value(0))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(2))
                .andExpect(jsonPath("$.items[0].canReturn").value(true));
    }

    @Test
    @DisplayName("2. Trả hàng hoàn tiền (RETURN_ONLY): Tồn kho tăng lại, sinh StockMovement RETURN, tính đúng tiền hoàn")
    void processReturn_returnOnly_shouldIncreaseStockAndRecordMovement() throws Exception {
        int initialStock = productRepository.findById(productPoloM.getId()).orElseThrow().getStockQuantity();

        OrderReturnRequest returnReq = OrderReturnRequest.builder()
                .orderCode(testOrder.getOrderCode())
                .reason(ReturnReason.WRONG_SIZE)
                .note("Khách mặc bị chật, trả lại 1 áo")
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(productPoloM.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnType").value("RETURN_ONLY"))
                .andExpect(jsonPath("$.totalRefundAmount").value(300000.00))
                .andExpect(jsonPath("$.totalExchangeAmount").value(0.00))
                .andExpect(jsonPath("$.netAmount").value(-300000.00))
                .andReturn();

        OrderReturnResponse res = objectMapper.readValue(result.getResponse().getContentAsString(), OrderReturnResponse.class);
        assertNotNull(res.getReturnCode());

        // Kiểm tra tồn kho Polo M tăng lên đúng 1
        Product updatedPoloM = productRepository.findById(productPoloM.getId()).orElseThrow();
        assertEquals(initialStock + 1, updatedPoloM.getStockQuantity());

        // Kiểm tra số lượng còn được trả trong getEligibleReturnInfo giảm còn 1
        EligibleReturnOrderResponse eligible = orderReturnService.getEligibleReturnInfo(testOrder.getOrderCode());
        assertEquals(1, eligible.getItems().get(0).getAlreadyReturnedQuantity());
        assertEquals(1, eligible.getItems().get(0).getRemainingQuantity());
    }

    @Test
    @DisplayName("3. Đổi hàng (EXCHANGE): Trả Polo M lấy Polo L, tính đúng tiền bù (+20.000đ), tồn kho cập nhật 2 chiều")
    void processReturn_exchange_shouldUpdateBothStocksAndCalculateNetAmount() throws Exception {
        int poloMStockBefore = productRepository.findById(productPoloM.getId()).orElseThrow().getStockQuantity();
        int poloLStockBefore = productRepository.findById(productPoloL.getId()).orElseThrow().getStockQuantity();

        // Trả 1 Polo M (300.000đ), lấy 1 Polo L (320.000đ) => Khách cần bù thêm +20.000đ
        OrderReturnRequest exchangeReq = OrderReturnRequest.builder()
                .orderCode(testOrder.getOrderCode())
                .reason(ReturnReason.WRONG_SIZE)
                .note("Khách đổi từ Size M sang Size L")
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(productPoloM.getId())
                                .quantity(1)
                                .build()
                ))
                .exchangeItems(List.of(
                        ExchangeItemRequest.builder()
                                .productId(productPoloL.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnType").value("EXCHANGE"))
                .andExpect(jsonPath("$.totalRefundAmount").value(300000.00))
                .andExpect(jsonPath("$.totalExchangeAmount").value(320000.00))
                .andExpect(jsonPath("$.netAmount").value(20000.00));

        // Polo M (món trả): tồn kho tăng +1
        Product poloMAfter = productRepository.findById(productPoloM.getId()).orElseThrow();
        assertEquals(poloMStockBefore + 1, poloMAfter.getStockQuantity());

        // Polo L (món đổi): tồn kho giảm -1
        Product poloLAfter = productRepository.findById(productPoloL.getId()).orElseThrow();
        assertEquals(poloLStockBefore - 1, poloLAfter.getStockQuantity());
    }

    @Test
    @DisplayName("4. Bắt lỗi: Trả vượt quá số lượng đã mua trả về HTTP 400 Bad Request")
    void processReturn_shouldReturn400_whenExceedingPurchasedQuantity() throws Exception {
        // Đơn hàng chỉ mua 2 chiếc, nhưng yêu cầu trả 3 chiếc
        OrderReturnRequest invalidReq = OrderReturnRequest.builder()
                .orderCode(testOrder.getOrderCode())
                .reason(ReturnReason.WRONG_SIZE)
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(productPoloM.getId())
                                .quantity(3)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Bắt lỗi: Trả sản phẩm không có trong đơn hàng trả về HTTP 400 Bad Request")
    void processReturn_shouldReturn400_whenProductNotInOrder() throws Exception {
        // Polo L không có trong testOrder (chỉ mua Polo M)
        OrderReturnRequest invalidReq = OrderReturnRequest.builder()
                .orderCode(testOrder.getOrderCode())
                .reason(ReturnReason.DEFECTIVE)
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(productPoloL.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Lấy chi tiết phiếu đổi trả theo ID và theo mã đơn hàng gốc")
    void getReturnById_andByOrderCode_shouldWorkProperly() throws Exception {
        OrderReturnRequest returnReq = OrderReturnRequest.builder()
                .orderCode(testOrder.getOrderCode())
                .reason(ReturnReason.COLOR_MISMATCH)
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(productPoloM.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderReturnResponse created = objectMapper.readValue(result.getResponse().getContentAsString(), OrderReturnResponse.class);

        // GET theo ID
        mockMvc.perform(get("/api/returns/" + created.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnCode").value(created.getReturnCode()));

        // GET theo mã đơn
        mockMvc.perform(get("/api/returns/by-order/" + testOrder.getOrderCode())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].returnCode").value(created.getReturnCode()));
    }

    @Test
    @DisplayName("7. Race condition (Đua lệnh): Hai thu ngân cùng xử lý trả cho một hóa đơn đồng thời -> Chỉ 1 người thành công, không bị trả gấp đôi")
    void processReturn_raceCondition_twoCashiersReturningSameOrder_shouldPreventDoubleReturn() throws Exception {
        Category category = categoryRepository.findByName("Áo Thời Trang")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Áo Thời Trang").build()));

        String suffix = String.valueOf(System.currentTimeMillis()).substring(6);

        // Tạo 1 sản phẩm có 10 cái trong kho
        Product raceProd = productRepository.save(Product.builder()
                .code("RACE_PROD_" + suffix)
                .name("Sản phẩm Test Concurrency " + suffix)
                .price(new BigDecimal("150000.00"))
                .stockQuantity(10)
                .category(category)
                .build());

        // Khách hàng mua đúng 1 cái
        Order raceOrder = orderService.createOrder(CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(raceProd.getId())
                                .quantity(1)
                                .build()
                ))
                .note("Đơn test concurrency race condition")
                .build());

        String raceOrderCode = raceOrder.getOrderCode();
        int initialStockAfterSale = productRepository.findById(raceProd.getId()).orElseThrow().getStockQuantity(); // 9

        // 2 Thu ngân cùng gửi request trả lại đúng 1 sản phẩm này tại cùng một thời điểm
        int threadCount = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String cashierName = "cashier_" + (i + 1);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Chờ phát súng xuất phát cùng lúc
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(cashierName, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                    );

                    OrderReturnRequest req = OrderReturnRequest.builder()
                            .orderCode(raceOrderCode)
                            .reason(ReturnReason.DEFECTIVE)
                            .returnItems(List.of(
                                    ReturnItemRequest.builder()
                                            .productId(raceProd.getId())
                                            .quantity(1)
                                            .build()
                            ))
                            .build();

                    orderReturnService.processReturn(req);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failCount.incrementAndGet();
                    exceptions.add(t);
                }
            });
        }

        readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        startLatch.countDown(); // Kích hoạt cả 2 luồng đua lệnh cùng 1 tích tắc
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(finished, "Tất cả các luồng phải hoàn thành trong thời gian quy định!");

        // 1. Kiểm tra kết quả: Đúng 1 thu ngân thành công, đúng 1 thu ngân thất bại
        assertEquals(1, successCount.get(), "Chỉ duy nhất 1 thu ngân được phép trả hàng thành công!");
        assertEquals(1, failCount.get(), "Thu ngân còn lại phải bị chặn lại với lỗi không còn đủ số lượng được trả!");

        // 2. Thu ngân thất bại phải nhận được IllegalArgumentException về số lượng cho phép trả
        Throwable failure = exceptions.get(0);
        while (failure.getCause() != null && !(failure instanceof IllegalArgumentException)) {
            failure = failure.getCause();
        }
        assertTrue(failure instanceof IllegalArgumentException, "Lỗi phải là IllegalArgumentException");
        assertTrue(failure.getMessage().contains("chỉ còn được trả tối đa 0 món") || failure.getMessage().contains("đã trả trước đó: 1"),
                "Thông báo lỗi phải chỉ rõ số lượng đã trả trước đó: " + failure.getMessage());

        // 3. Kiểm tra DB: Số lượng đã trả thực tế chỉ đúng bằng 1
        int returnedInDb = orderReturnRepository.countReturnedQuantityByOrderAndProduct(raceOrder.getId(), raceProd.getId());
        assertEquals(1, returnedInDb, "Số lượng đã trả trong DB phải chính xác là 1, không được gấp đôi!");

        // 4. Tồn kho sản phẩm chỉ được hoàn lại +1 (từ 9 lên 10, không phải 11)
        Product finalProd = productRepository.findById(raceProd.getId()).orElseThrow();
        assertEquals(initialStockAfterSale + 1, finalProd.getStockQuantity(), "Tồn kho chỉ được tăng +1 cho món hàng trả hợp lệ!");
    }
}
