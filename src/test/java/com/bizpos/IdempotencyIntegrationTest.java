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
}
