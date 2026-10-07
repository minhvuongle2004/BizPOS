package com.bizpos;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.Product;
import com.bizpos.exception.InsufficientStockException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Integration Tests cho Xử lý Đồng Thời (Concurrency & Pessimistic Locking) trên MySQL")
public class OrderConcurrencyIntegrationTest {

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

    private Category testCategory;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name("Danh mục Concurrency " + System.currentTimeMillis())
                        .description("Test Concurrency Category")
                        .build()));

        testCustomer = customerRepository.findAll().stream().findFirst().orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .fullName("Khách hàng Concurrency " + System.currentTimeMillis())
                        .phone("09" + String.valueOf(System.currentTimeMillis()).substring(5))
                        .email("concurrent_" + System.currentTimeMillis() + "@bizpos.vn")
                        .build()));
    }

    @Test
    @DisplayName("Concurrency: 20 threads đồng thời tranh mua sản phẩm có tồn kho = 10 -> đúng 10 đơn thành công, 10 đơn hết hàng, tồn kho về đúng 0")
    void concurrentOrderCreation_singleProduct_exactStockDeduction() throws InterruptedException {
        // 1. Tạo sản phẩm với tồn kho ban đầu = 10
        String code = "CONCUR_P1_" + System.currentTimeMillis();
        Product product = productRepository.save(Product.builder()
                .code(code)
                .name("Sản phẩm Tranh Mua Đồng Thời")
                .price(new BigDecimal("50000.00"))
                .stockQuantity(10) // Chỉ có 10 sản phẩm
                .category(testCategory)
                .build());

        int totalThreads = 20; // 20 luồng đồng thời tranh mua mỗi luồng 1 cái
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startGun = new CountDownLatch(1); // Phát súng bắt đầu đồng thời
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // 2. Chuẩn bị 20 nhiệm vụ đặt hàng
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Chờ tiếng súng xuất phát để 20 luồng cùng lao vào tại 1 thời điểm
                    startGun.await();

                    CreateOrderRequest req = CreateOrderRequest.builder()
                            .customerId(testCustomer.getId())
                            .note("Đơn đồng thời luồng " + Thread.currentThread().getName())
                            .items(List.of(OrderItemRequest.builder()
                                    .productId(product.getId())
                                    .quantity(1)
                                    .build()))
                            .build();

                    Order order = orderService.createOrder(req);
                    if (order != null && order.getId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (InsufficientStockException ex) {
                    outOfStockCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrorCount.incrementAndGet();
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Chờ tất cả 20 thread sẵn sàng ở vạch xuất phát
        readyLatch.await(5, TimeUnit.SECONDS);
        // Nổ súng xuất phát đồng thời
        startGun.countDown();
        // Chờ tất cả 20 thread xử lý xong
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "Các luồng phải hoàn thành trong thời gian quy định");

        // 3. Kiểm tra kết quả
        System.out.println("====== KẾT QUẢ TEST ĐỒNG THỜI (CONCURRENCY) ======");
        System.out.println("Tổng số luồng: " + totalThreads);
        System.out.println("Số đơn đặt thành công: " + successCount.get());
        System.out.println("Số đơn bị chặn do hết hàng: " + outOfStockCount.get());
        System.out.println("Lỗi không lường trước: " + unexpectedErrorCount.get());

        if (!errors.isEmpty()) {
            errors.forEach(e -> e.printStackTrace());
        }

        // Đảm bảo không có lỗi hệ thống (deadlock hay exception lạ)
        assertEquals(0, unexpectedErrorCount.get(), "Không được có lỗi ngoài InsufficientStockException");

        // Đúng 10 đơn thành công vì ban đầu kho chỉ có 10
        assertEquals(10, successCount.get(), "Chính xác 10 đơn phải thành công");

        // Đúng 10 đơn thất bại vì các đơn sau thấy kho đã về 0
        assertEquals(10, outOfStockCount.get(), "Chính xác 10 đơn phải bị từ chối vì hết hàng");

        // 4. Kiểm tra dữ liệu thực tế trong DB: Tồn kho phải về ĐÚNG 0 (không bao giờ âm hay lệch số)
        Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, finalProduct.getStockQuantity(), "Tồn kho trong MySQL phải về đúng 0");
    }

    @Test
    @DisplayName("Deadlock Prevention: 2 luồng mua chéo nhau (A rồi B vs B rồi A) không bị Deadlock nhờ cơ chế sort productId tăng dần")
    void concurrentOrderCreation_multipleProducts_preventsDeadlock() throws InterruptedException {
        // 1. Tạo 2 sản phẩm A và B
        Product productA = productRepository.save(Product.builder()
                .code("DEADLOCK_A_" + System.currentTimeMillis())
                .name("Sản phẩm A")
                .price(new BigDecimal("30000.00"))
                .stockQuantity(10)
                .category(testCategory)
                .build());

        Product productB = productRepository.save(Product.builder()
                .code("DEADLOCK_B_" + System.currentTimeMillis())
                .name("Sản phẩm B")
                .price(new BigDecimal("40000.00"))
                .stockQuantity(10)
                .category(testCategory)
                .build());

        // Đảm bảo ID phân biệt
        Long idA = Math.min(productA.getId(), productB.getId());
        Long idB = Math.max(productA.getId(), productB.getId());

        int pairs = 5; // 5 cặp = 10 luồng đặt hàng đồng thời
        int totalThreads = pairs * 2;
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < pairs; i++) {
            // Nhóm 1: Request gửi theo thứ tự: [idA, idB]
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startGun.await();
                    CreateOrderRequest req = CreateOrderRequest.builder()
                            .customerId(testCustomer.getId())
                            .items(List.of(
                                    OrderItemRequest.builder().productId(idA).quantity(1).build(),
                                    OrderItemRequest.builder().productId(idB).quantity(1).build()
                            ))
                            .build();
                    orderService.createOrder(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Nhóm 2: Request gửi theo thứ tự NGƯỢC LẠI: [idB, idA]
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startGun.await();
                    CreateOrderRequest req = CreateOrderRequest.builder()
                            .customerId(testCustomer.getId())
                            .items(List.of(
                                    OrderItemRequest.builder().productId(idB).quantity(1).build(),
                                    OrderItemRequest.builder().productId(idA).quantity(1).build()
                            ))
                            .build();
                    orderService.createOrder(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startGun.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "Tất cả các luồng phải hoàn thành");
        assertEquals(0, errorCount.get(), "Không được xảy ra Deadlock hay lỗi xung đột khóa");
        assertEquals(totalThreads, successCount.get(), "Toàn bộ 10 đơn hàng phải hoàn tất thành công");

        // Tồn kho mỗi sản phẩm ban đầu là 10, bị trừ đúng 10 cái -> về đúng 0
        Product finalA = productRepository.findById(idA).orElseThrow();
        Product finalB = productRepository.findById(idB).orElseThrow();
        assertEquals(0, finalA.getStockQuantity(), "Tồn kho A phải về đúng 0");
        assertEquals(0, finalB.getStockQuantity(), "Tồn kho B phải về đúng 0");
    }
}
