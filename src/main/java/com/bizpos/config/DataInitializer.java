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
                    .category(sampleCategory)
                    .build();
            productRepository.save(sampleProduct);
            log.info(">> [GHI DB THÀNH CÔNG] Đã tạo sản phẩm mẫu: {} (Mã: {})", sampleProduct.getName(), sampleProduct.getCode());
        }

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

        // 4. Kiểm tra Đọc dữ liệu (Read)
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng danh mục: {}", categoryRepository.count());
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng sản phẩm: {}", productRepository.count());
        log.info(">> [ĐỌC DB THÀNH CÔNG] Số lượng khách hàng: {}", customerRepository.count());
        log.info("===============================================================================");
    }
}
