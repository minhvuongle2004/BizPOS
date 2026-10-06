package com.bizpos.config;

import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Product;
import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final com.bizpos.repository.OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("========== BIZPOS: BẮT ĐẦU KIỂM TRA ĐỌC/GHI DATABASE QUA REPOSITORY ==========");

        // 0. Khởi tạo tài khoản mẫu ADMIN và STAFF
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .build();
            userRepository.save(admin);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo tài khoản mẫu: admin (Role: ADMIN, Mật khẩu: admin123)");

            User staff = User.builder()
                    .username("staff")
                    .password(passwordEncoder.encode("staff123"))
                    .role(Role.STAFF)
                    .build();
            userRepository.save(staff);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo tài khoản mẫu: staff (Role: STAFF, Mật khẩu: staff123)");
        }

        // 1. Kiểm tra bảng Danh mục (Category)
        if (categoryRepository.count() == 0) {
            Category sampleCategory = Category.builder()
                    .name("Đồ uống")
                    .description("Các loại nước ngọt, cà phê, trà đóng chai")
                    .build();
            categoryRepository.save(sampleCategory);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo danh mục mẫu: {}", sampleCategory.getName());

            // 2. Kiểm tra bảng Sản phẩm (Product) gắn với Danh mục vừa tạo
            Product sampleProduct = Product.builder()
                    .code("SP001")
                    .name("Cà phê sữa đá")
                    .price(new BigDecimal("25000.00"))
                    .description("Cà phê pha phin truyền thống thơm ngon")
                    .stockQuantity(100)
                    .category(sampleCategory)
                    .build();
            productRepository.save(sampleProduct);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo sản phẩm mẫu: {} (Mã: {}, Tồn kho: 100)", sampleProduct.getName(), sampleProduct.getCode());
        }

        // Cập nhật tồn kho mặc định cho các sản phẩm hiện có nếu chưa có số lượng tồn kho
        productRepository.findAll().forEach(p -> {
            if (p.getStockQuantity() == null || p.getStockQuantity() == 0) {
                p.setStockQuantity(100);
                productRepository.save(p);
            }
        });

        // 3. Kiểm tra bảng Khách hàng (Customer)
        if (customerRepository.count() == 0) {
            Customer sampleCustomer = Customer.builder()
                    .fullName("Nguyễn Văn A")
                    .phone("0987654321")
                    .email("nguyenvana@example.com")
                    .address("123 Cầu Giấy, Hà Nội")
                    .build();
            customerRepository.save(sampleCustomer);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo khách hàng mẫu: {} (SĐT: {})", sampleCustomer.getFullName(), sampleCustomer.getPhone());
        }

        // 4. Kiểm tra bảng Đơn hàng (Order)
        if (orderRepository.count() == 0) {
            Customer customer = customerRepository.findAll().stream().findFirst().orElse(null);
            Product product = productRepository.findAll().stream().findFirst().orElse(null);

            if (product != null) {
                // Đơn hàng 1: Hôm nay
                com.bizpos.entity.Order order1 = com.bizpos.entity.Order.builder()
                        .orderCode("HD-" + System.currentTimeMillis() % 100000)
                        .customer(customer)
                        .orderDate(java.time.LocalDateTime.now().minusHours(2))
                        .totalAmount(product.getPrice().multiply(new BigDecimal("2")))
                        .note("Thanh toán tiền mặt tại quầy")
                        .build();

                com.bizpos.entity.OrderItem item1 = com.bizpos.entity.OrderItem.builder()
                        .product(product)
                        .productName(product.getName())
                        .unitPrice(product.getPrice())
                        .quantity(2)
                        .lineTotal(product.getPrice().multiply(new BigDecimal("2")))
                        .build();
                order1.addItem(item1);
                orderRepository.save(order1);

                // Đơn hàng 2: Hôm qua
                com.bizpos.entity.Order order2 = com.bizpos.entity.Order.builder()
                        .orderCode("HD-" + (System.currentTimeMillis() + 1) % 100000)
                        .customer(customer)
                        .orderDate(java.time.LocalDateTime.now().minusDays(1).minusHours(4))
                        .totalAmount(product.getPrice())
                        .note("Khách mang về")
                        .build();

                com.bizpos.entity.OrderItem item2 = com.bizpos.entity.OrderItem.builder()
                        .product(product)
                        .productName(product.getName())
                        .unitPrice(product.getPrice())
                        .quantity(1)
                        .lineTotal(product.getPrice())
                        .build();
                order2.addItem(item2);
                orderRepository.save(order2);

                // Đơn hàng 3: 2 ngày trước (Khách vãng lai)
                com.bizpos.entity.Order order3 = com.bizpos.entity.Order.builder()
                        .orderCode("HD-" + (System.currentTimeMillis() + 2) % 100000)
                        .customer(null)
                        .orderDate(java.time.LocalDateTime.now().minusDays(2).minusHours(1))
                        .totalAmount(product.getPrice().multiply(new BigDecimal("3")))
                        .note("Khách vãng lai chuyển khoản")
                        .build();

                com.bizpos.entity.OrderItem item3 = com.bizpos.entity.OrderItem.builder()
                        .product(product)
                        .productName(product.getName())
                        .unitPrice(product.getPrice())
                        .quantity(3)
                        .lineTotal(product.getPrice().multiply(new BigDecimal("3")))
                        .build();
                order3.addItem(item3);
                orderRepository.save(order3);

                log.info(">> [GHI DB THÀNH CÔNG] Đã tạo 3 đơn hàng mẫu vào CSDL.");
            }
        }

        // 5. Kiểm tra Đọc dữ liệu (Read)
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng danh mục: {}", categoryRepository.count());
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng sản phẩm: {}", productRepository.count());
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng khách hàng: {}", customerRepository.count());
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng đơn hàng: {}", orderRepository.count());
        log.info("===============================================================================");
    }
}
