package com.bizpos;

import com.bizpos.dto.*;
import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.Product;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests cho Idempotency-Key (Chống tạo đơn / trả hàng lặp khi bấm đúp)")
public class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private com.bizpos.repository.IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private com.bizpos.service.IdempotencyService idempotencyService;

    private String staffToken;
    private Product testProduct;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        staffToken = "Bearer " + jwtTokenProvider.generateToken("staff", "ROLE_STAFF");

        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder().name("Category Idempotency").description("Test").build();
            return categoryRepository.save(cat);
        });

        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        testProduct = Product.builder()
                .code("IDEM-" + suffix)
                .name("Sản phẩm Idempotent " + suffix)
                .price(new BigDecimal("100000"))
                .stockQuantity(50)
                .category(category)
                .build();
        testProduct = productRepository.save(testProduct);

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() -> {
            Customer c = Customer.builder()
                    .fullName("Khách Idempotent")
                    .phone("098" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 7))
                    .build();
            return customerRepository.save(c);
        });
    }

    @Test
    @DisplayName("1. Bấm đúp khi tạo đơn (POST /api/orders với cùng Idempotency-Key): Trả về cùng đơn, chỉ trừ kho 1 lần duy nhất")
    void testCreateOrder_DuplicateIdempotencyKey_DeductsStockOnlyOnce() throws Exception {
        String idempotencyKey = "order-idem-" + UUID.randomUUID();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn test Idempotency")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(5)
                                .build()
                ))
                .build();

        // Lần 1: Thu ngân gửi request tạo đơn
        MvcResult result1 = mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse order1 = objectMapper.readValue(result1.getResponse().getContentAsString(), OrderResponse.class);
        assertNotNull(order1.getOrderCode());

        // Kiểm tra tồn kho sau lần 1: 50 - 5 = 45
        Product productAfterFirst = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(45, productAfterFirst.getStockQuantity(), "Tồn kho phải giảm từ 50 xuống 45 sau lần tạo đầu");

        // Lần 2: Mạng chập chờn / Thu ngân bấm đúp gửi lại cùng Idempotency-Key
        MvcResult result2 = mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse order2 = objectMapper.readValue(result2.getResponse().getContentAsString(), OrderResponse.class);

        // Khẳng định: Kết quả trả về từ cache trùng khớp 100% với lần 1 (cùng mã đơn, cùng tổng tiền)
        assertEquals(order1.getId(), order2.getId(), "Cả 2 lần phải trả về cùng 1 mã đơn ID");
        assertEquals(order1.getOrderCode(), order2.getOrderCode(), "Cả 2 lần phải trả về cùng mã đơn hàng");

        // Khẳng định QUAN TRỌNG NHẤT: Tồn kho KHÔNG bị trừ lần 2! (Vẫn giữ nguyên 45, không bị tụt xuống 40)
        Product productAfterSecond = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(45, productAfterSecond.getStockQuantity(), "Tồn kho KHÔNG được trừ lần 2 khi dùng chung Idempotency-Key!");
    }

    @Test
    @DisplayName("2. Không gửi header Idempotency-Key: Hoạt động bình thường (Tương thích ngược)")
    void testCreateOrder_WithoutIdempotencyKey_WorksNormally() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn không truyền Idempotency-Key")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCode").isNotEmpty());
    }

    @Test
    @DisplayName("3. Đổi trả hàng (POST /api/returns với cùng Idempotency-Key): Trả về cùng phiếu, không hoàn tiền hay cộng kho 2 lần")
    void testProcessReturn_DuplicateIdempotencyKey_RefundsOnlyOnce() throws Exception {
        // Tạo trước 1 đơn hàng hợp lệ để đổi trả
        CreateOrderRequest orderReq = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn để test đổi trả Idempotency")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(4)
                                .build()
                ))
                .build();
        Order createdOrder = orderService.createOrder(orderReq);

        // Tồn kho sau khi mua 4 cái: 50 - 4 = 46
        Product pAfterBuy = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(46, pAfterBuy.getStockQuantity());

        String retIdempotencyKey = "return-idem-" + UUID.randomUUID();

        OrderReturnRequest returnReq = OrderReturnRequest.builder()
                .orderCode(createdOrder.getOrderCode())
                .returnType(com.bizpos.enums.ReturnType.RETURN_ONLY)
                .reason(com.bizpos.enums.ReturnReason.CUSTOMER_CHANGE_MIND)
                .note("Khách đổi ý trả 2 cái")
                .returnItems(List.of(
                        ReturnItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(2)
                                .reason(com.bizpos.enums.ReturnReason.CUSTOMER_CHANGE_MIND)
                                .build()
                ))
                .build();

        // Lần 1: Gửi yêu cầu đổi trả
        MvcResult res1 = mockMvc.perform(post("/api/returns")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", retIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderReturnResponse ret1 = objectMapper.readValue(res1.getResponse().getContentAsString(), OrderReturnResponse.class);
        assertNotNull(ret1.getReturnCode());

        // Tồn kho sau khi trả 2 cái: 46 + 2 = 48
        Product pAfterRet1 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(48, pAfterRet1.getStockQuantity());

        // Lần 2: Bấm đúp gửi lại cùng Idempotency-Key
        MvcResult res2 = mockMvc.perform(post("/api/returns")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", retIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderReturnResponse ret2 = objectMapper.readValue(res2.getResponse().getContentAsString(), OrderReturnResponse.class);

        // Khẳng định: Kết quả lần 2 trả về từ cache giống hệt lần 1
        assertEquals(ret1.getReturnCode(), ret2.getReturnCode(), "Cả 2 lần phải trả về cùng mã phiếu đổi trả");
        assertEquals(ret1.getId(), ret2.getId(), "Cả 2 lần phải trả về cùng ID phiếu đổi trả");

        // Khẳng định: Tồn kho KHÔNG bị cộng thừa lần 2 (Vẫn là 48, không bị cộng lên 50)
        Product pAfterRet2 = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(48, pAfterRet2.getStockQuantity(), "Tồn kho không được hoàn thừa lần 2 khi trùng Idempotency-Key!");
    }

    @Test
    @DisplayName("4. Test đồng thời (Nhiều thread, cùng key): Đúng 1 lần trừ kho, các thread khác nhận 201 (cache) hoặc 409 (conflict)")
    void testConcurrentOrders_SameIdempotencyKey_DeductsStockOnlyOnce() throws Exception {
        String sharedKey = "concurrent-order-" + UUID.randomUUID();
        int initialStock = testProduct.getStockQuantity();
        int buyQty = 5;

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn test đa luồng Idempotency")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(buyQty)
                                .build()
                ))
                .build();

        String requestBody = objectMapper.writeValueAsString(request);

        int numThreads = 4;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(numThreads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger success201Count = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflict409Count = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger otherStatusCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<String> returnedOrderCodes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/orders")
                                    .header("Authorization", staffToken)
                                    .header("Idempotency-Key", sharedKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 201) {
                        success201Count.incrementAndGet();
                        OrderResponse resp = objectMapper.readValue(res.getResponse().getContentAsString(), OrderResponse.class);
                        returnedOrderCodes.add(resp.getOrderCode());
                    } else if (status == 409) {
                        conflict409Count.incrementAndGet();
                    } else {
                        otherStatusCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));

        // Phải có ít nhất 1 request thành công 201
        assertTrue(success201Count.get() >= 1, "Ít nhất 1 thread phải thành công (201 Created)");
        assertEquals(0, otherStatusCount.get(), "Không được có status lỗi nào khác ngoài 201 hoặc 409");
        assertEquals(numThreads, success201Count.get() + conflict409Count.get(), "Tất cả các thread phải kết thúc với 201 hoặc 409");

        // Nếu có nhiều hơn 1 thread nhận 201 (do cache hit), tất cả phải trả về cùng 1 mã đơn hàng duy nhất!
        if (!returnedOrderCodes.isEmpty()) {
            String firstCode = returnedOrderCodes.get(0);
            for (String code : returnedOrderCodes) {
                assertEquals(firstCode, code, "Mọi response 201 đều phải trả về chung 1 mã đơn");
            }
        }

        // Khẳng định: Kho chỉ bị trừ đúng 1 lần duy nhất!
        Product refreshed = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(initialStock - buyQty, refreshed.getStockQuantity(),
                "Tồn kho chỉ được trừ đúng 1 lần duy nhất cho toàn bộ các luồng cùng key!");
    }

    @Test
    @DisplayName("5. Cùng Idempotency-Key nhưng khác nội dung Payload: Trả về HTTP 422 Unprocessable Entity")
    void testCreateOrder_SameKeyDifferentPayload_Returns422UnprocessableEntity() throws Exception {
        String sharedKey = "payload-mismatch-" + UUID.randomUUID();

        // Request 1: Mua số lượng 2
        CreateOrderRequest request1 = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn hàng thứ nhất")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Request 2: Cùng key nhưng mua số lượng 10 và note khác (Payload khác!)
        CreateOrderRequest request2 = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn hàng thứ hai đã đổi nội dung")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nội dung khác")));
    }

    @Test
    @DisplayName("6. Request thất bại (lỗi thiếu tồn kho / lỗi nghiệp vụ): Key được giải phóng, cho phép Retry thành công")
    void testCreateOrder_FailedRequest_AllowsRetryWithSameKey() throws Exception {
        String retryKey = "retry-key-" + UUID.randomUUID();

        // Lần 1: Mua vượt quá tồn kho (mua 9999 cái) -> Thất bại (400 Bad Request)
        CreateOrderRequest failRequest = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn vượt tồn kho")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(9999)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isBadRequest());

        // Lần 2: Sửa lại số lượng hợp lệ (mua 3 cái) và gửi lại với CÙNG key đó -> Phải thành công 201 Created!
        CreateOrderRequest successRequest = CreateOrderRequest.builder()
                .customerId(testCustomer.getId())
                .note("Đơn vượt tồn kho")
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(3)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffToken)
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCode").isNotEmpty());

        // Tồn kho phải giảm đúng 3
        Product p = productRepository.findById(testProduct.getId()).orElseThrow();
        assertEquals(47, p.getStockQuantity());
    }

    @Test
    @DisplayName("7. Dọn dẹp bản ghi Idempotency cũ quá hạn bằng Scheduled Service")
    void testCleanupOldRecords_DeletesExpiredKeysOnly() {
        String freshKey = "fresh-key-" + UUID.randomUUID();

        // Tạo 1 key mới
        idempotencyService.startExecution(freshKey, "/api/orders", "hash1");
        idempotencyService.completeExecution(freshKey, 201, "{}");

        // Dọn dẹp bản ghi cũ hơn 1 ngày
        idempotencyService.cleanupOldRecords(1);

        // freshKey vừa tạo phải còn tồn tại
        assertTrue(idempotencyRecordRepository.findByIdempotencyKey(freshKey).isPresent(), "Fresh key phải còn tồn tại");
    }
}
