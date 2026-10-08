package com.bizpos.config;

import com.bizpos.entity.Category;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Product;
import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderItem;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.UserRepository;
import com.bizpos.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final StockMovementService stockMovementService;

    @Override
    public void run(String... args) {
        log.info("========== BIZPOS: KHỞI TẠO VÀ CHUẨN HÓA DỮ LIỆU BÁN LẺ THỜI TRANG ==========");

        // 0. Khởi tạo tài khoản mẫu ADMIN và STAFF
        initUsers();

        // 1. Khởi tạo Danh mục và Sản phẩm Thời trang (Size, Màu sắc, Chất liệu)
        initFashionCatalog();

        // 2. Khởi tạo Khách hàng mẫu
        initCustomers();

        // 3. Khởi tạo Đơn hàng Thời trang mẫu (để sẵn sàng test Đổi - Trả hàng)
        initSampleFashionOrders();

        log.info(">> [HOÀN TẤT SEED DATA] Danh mục: {}, Sản phẩm: {}, Khách hàng: {}, Đơn hàng: {}",
                categoryRepository.count(), productRepository.count(), customerRepository.count(), orderRepository.count());
        log.info("===============================================================================");
    }

    private void initUsers() {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .build();
            userRepository.save(admin);
            log.info(">> [TẠO TÀI KHOẢN] admin (Role: ADMIN, Mật khẩu: admin123)");

            User staff = User.builder()
                    .username("staff")
                    .password(passwordEncoder.encode("staff123"))
                    .role(Role.STAFF)
                    .build();
            userRepository.save(staff);
            log.info(">> [TẠO TÀI KHOẢN] staff (Role: STAFF, Mật khẩu: staff123)");
        }
    }

    private void initFashionCatalog() {
        // 1. Danh mục 1: Áo Thời Trang
        Category catAo = getOrCreateCategory("Áo Thời Trang", "Áo thun polo, áo sơ mi, áo khoác các kiểu dáng hiện đại");
        createProductIfNotExist("POLO-BLK-M", "Áo Polo Cotton Pique", new BigDecimal("299000"), "M", "Đen", "Cotton Pique 100%", 50, "Co giãn 4 chiều, chống nhăn thoáng khí", catAo);
        createProductIfNotExist("POLO-BLK-L", "Áo Polo Cotton Pique", new BigDecimal("299000"), "L", "Đen", "Cotton Pique 100%", 45, "Co giãn 4 chiều, chống nhăn thoáng khí", catAo);
        createProductIfNotExist("POLO-WHT-M", "Áo Polo Cotton Pique", new BigDecimal("299000"), "M", "Trắng", "Cotton Pique 100%", 40, "Form Regular fit lịch lãm, dễ phối đồ", catAo);
        createProductIfNotExist("POLO-WHT-L", "Áo Polo Cotton Pique", new BigDecimal("299000"), "L", "Trắng", "Cotton Pique 100%", 35, "Form Regular fit lịch lãm, dễ phối đồ", catAo);
        createProductIfNotExist("SOMI-OXF-WHT-L", "Áo Sơ Mi Oxford Dài Tay", new BigDecimal("380000"), "L", "Trắng", "Cotton Oxford", 30, "Chất vải dày dặn, đứng form chuẩn công sở", catAo);
        createProductIfNotExist("SOMI-OXF-BLU-M", "Áo Sơ Mi Oxford Dài Tay", new BigDecimal("380000"), "M", "Xanh Pastel", "Cotton Oxford", 25, "Tone xanh nhẹ nhàng, trẻ trung", catAo);
        createProductIfNotExist("KHOAC-BOM-M", "Áo Khoác Bomber Gió Kaki", new BigDecimal("550000"), "M", "Xanh Rêu", "Kaki 2 lớp lót gió", 20, "Khóa kéo kim loại bền bỉ, phong cách dạo phố", catAo);

        // 2. Danh mục 2: Quần & Chân Váy
        Category catQuan = getOrCreateCategory("Quần & Chân Váy", "Quần jeans slimfit, quần âu công sở, quần short dạo phố");
        createProductIfNotExist("JEAN-SLIM-BLU-30", "Quần Jeans Slimfit Co Giãn", new BigDecimal("450000"), "30", "Xanh Indigo", "Denim Spandex", 40, "Tôn dáng, co giãn tốt thoải mái vận động", catQuan);
        createProductIfNotExist("JEAN-SLIM-BLU-31", "Quần Jeans Slimfit Co Giãn", new BigDecimal("450000"), "31", "Xanh Indigo", "Denim Spandex", 35, "Tôn dáng, co giãn tốt thoải mái vận động", catQuan);
        createProductIfNotExist("TAY-AU-BLK-30", "Quần Tây Âu Dáng Suông", new BigDecimal("420000"), "30", "Đen", "Tuyết mưa cao cấp", 30, "Vải đứng form, không nhăn, cạp đai may kỹ", catQuan);
        createProductIfNotExist("TAY-AU-BLK-31", "Quần Tây Âu Dáng Suông", new BigDecimal("420000"), "31", "Đen", "Tuyết mưa cao cấp", 28, "Vải đứng form, không nhăn, cạp đai may kỹ", catQuan);
        createProductIfNotExist("SHORT-KAKI-BE-L", "Quần Short Kaki Dạo Phố", new BigDecimal("250000"), "L", "Be", "Kaki co giãn nhẹ", 50, "Trẻ trung, năng động cho mùa hè", catQuan);

        // 3. Danh mục 3: Giày Dép
        Category catGiay = getOrCreateCategory("Giày Dép", "Sneaker thể thao, giày da loafer, sandal thời trang");
        createProductIfNotExist("SNK-RETRO-WHT-40", "Giày Sneaker Retro Vintage", new BigDecimal("650000"), "40", "Trắng Ngà", "Da Microfiber & Đế cao su", 25, "Đệm êm chân, chống trơn trượt hiệu quả", catGiay);
        createProductIfNotExist("SNK-RETRO-WHT-41", "Giày Sneaker Retro Vintage", new BigDecimal("650000"), "41", "Trắng Ngà", "Da Microfiber & Đế cao su", 20, "Đệm êm chân, chống trơn trượt hiệu quả", catGiay);
        createProductIfNotExist("SNK-RETRO-WHT-42", "Giày Sneaker Retro Vintage", new BigDecimal("650000"), "42", "Trắng Ngà", "Da Microfiber & Đế cao su", 18, "Đệm êm chân, chống trơn trượt hiệu quả", catGiay);
        createProductIfNotExist("LOAFER-PEN-BRW-41", "Giày Da Loafer Penny", new BigDecimal("790000"), "41", "Nâu Bò", "Da bò nguyên tấm", 15, "Kiểu dáng thanh lịch cho quý ông công sở", catGiay);

        // 4. Danh mục 4: Phụ Kiện
        Category catPhuKien = getOrCreateCategory("Phụ Kiện", "Thắt lưng da, mũ nón, ví bóp, phụ kiện thời trang");
        createProductIfNotExist("THAT-LUNG-BLK-FREE", "Thắt Lưng Da Khóa Tự Động", new BigDecimal("220000"), "Freesize", "Đen", "Da bò dập vân", 60, "Mặt khóa hợp kim chống rỉ sét", catPhuKien);
        createProductIfNotExist("MU-LUOI-BE-FREE", "Mũ Lưỡi Trai Classic Vintage", new BigDecimal("150000"), "Freesize", "Be", "Kaki Wash", 80, "Khóa kim loại chỉnh cỡ vòng đầu tiện lợi", catPhuKien);
    }

    private Category getOrCreateCategory(String name, String description) {
        return categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            Category cat = Category.builder()
                    .name(name)
                    .description(description)
                    .build();
            Category saved = categoryRepository.save(cat);
            log.info(">> [TẠO DANH MỤC THỜI TRANG] {}", saved.getName());
            return saved;
        });
    }

    private void createProductIfNotExist(String code, String name, BigDecimal price, String size, String color, String material, int stock, String desc, Category category) {
        if (!productRepository.existsByCode(code)) {
            Product product = Product.builder()
                    .code(code)
                    .name(name)
                    .price(price)
                    .size(size)
                    .color(color)
                    .material(material)
                    .stockQuantity(stock)
                    .description(desc)
                    .category(category)
                    .build();
            Product saved = productRepository.save(product);

            // Ghi nhận nhật ký nhập kho ban đầu
            stockMovementService.recordMovement(
                    saved,
                    com.bizpos.entity.MovementType.IMPORT,
                    stock,
                    0,
                    stock,
                    "INIT-" + code,
                    "Khởi tạo kho sản phẩm thời trang",
                    "SYSTEM"
            );
            log.info(">> [TẠO SẢN PHẨM THỜI TRANG] {} (Size: {}, Màu: {}, Giá: {} đ)", name, size, color, price);
        }
    }

    private void initCustomers() {
        if (customerRepository.count() == 0) {
            Customer c1 = Customer.builder()
                    .fullName("Nguyễn Văn A")
                    .phone("0987654321")
                    .email("nguyenvana@example.com")
                    .address("123 Cầu Giấy, Hà Nội")
                    .build();
            customerRepository.save(c1);

            Customer c2 = Customer.builder()
                    .fullName("Trần Thị Mai")
                    .phone("0912345678")
                    .email("tranmai@example.com")
                    .address("45 Hai Bà Trưng, Hoàn Kiếm, Hà Nội")
                    .build();
            customerRepository.save(c2);

            Customer c3 = Customer.builder()
                    .fullName("Lê Hoàng Long")
                    .phone("0909888999")
                    .email("longle@example.com")
                    .address("88 Nguyễn Trãi, Thanh Xuân, Hà Nội")
                    .build();
            customerRepository.save(c3);

            log.info(">> [TẠO KHÁCH HÀNG MẪU] Đã tạo 3 khách hàng thành công.");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    protected void initSampleFashionOrders() {
        // Chỉ tạo đơn hàng mẫu thời trang nếu chưa có đơn hàng HD-FASHION-01
        boolean hasFashionOrders = orderRepository.findByOrderCode("HD-FASHION-01").isPresent();

        if (!hasFashionOrders) {
            Customer customer1 = customerRepository.findByPhone("0987654321").orElse(null);
            Customer customer2 = customerRepository.findByPhone("0912345678").orElse(null);

            Optional<Product> poloBlackM = productRepository.findByCode("POLO-BLK-M");
            Optional<Product> jeanSlim30 = productRepository.findByCode("JEAN-SLIM-BLU-30");
            Optional<Product> sneaker41 = productRepository.findByCode("SNK-RETRO-WHT-41");

            if (poloBlackM.isPresent() && jeanSlim30.isPresent()) {
                // Đơn hàng 1: Khách Nguyễn Văn A mua Áo Polo Đen Size M (Rất phù hợp để test Đổi sang Size L)
                Product polo = poloBlackM.get();
                Product jean = jeanSlim30.get();

                Order order1 = Order.builder()
                        .orderCode("HD-FASHION-01")
                        .customer(customer1)
                        .orderDate(LocalDateTime.now().minusDays(1).minusHours(3))
                        .totalAmount(polo.getPrice().add(jean.getPrice()))
                        .note("Khách mua tại cửa hàng (Chờ thử size)")
                        .build();

                OrderItem item1 = OrderItem.builder()
                        .product(polo)
                        .productName(polo.getName() + " - " + polo.getSize() + " / " + polo.getColor())
                        .unitPrice(polo.getPrice())
                        .quantity(1)
                        .lineTotal(polo.getPrice())
                        .build();

                OrderItem item2 = OrderItem.builder()
                        .product(jean)
                        .productName(jean.getName() + " - " + jean.getSize() + " / " + jean.getColor())
                        .unitPrice(jean.getPrice())
                        .quantity(1)
                        .lineTotal(jean.getPrice())
                        .build();

                order1.addItem(item1);
                order1.addItem(item2);
                orderRepository.save(order1);

                log.info(">> [TẠO ĐƠN HÀNG THỜI TRANG MẪU] Mã đơn: {} (Khách: Nguyễn Văn A)", order1.getOrderCode());
            }

            if (sneaker41.isPresent()) {
                // Đơn hàng 2: Khách Trần Thị Mai mua Giày Sneaker Retro Size 41
                Product snk = sneaker41.get();
                Order order2 = Order.builder()
                        .orderCode("HD-FASHION-02")
                        .customer(customer2)
                        .orderDate(LocalDateTime.now().minusHours(5))
                        .totalAmount(snk.getPrice())
                        .note("Thanh toán chuyển khoản ngân hàng")
                        .build();

                OrderItem item3 = OrderItem.builder()
                        .product(snk)
                        .productName(snk.getName() + " - Size " + snk.getSize())
                        .unitPrice(snk.getPrice())
                        .quantity(1)
                        .lineTotal(snk.getPrice())
                        .build();

                order2.addItem(item3);
                orderRepository.save(order2);

                log.info(">> [TẠO ĐƠN HÀNG THỜI TRANG MẪU] Mã đơn: {} (Khách: Trần Thị Mai)", order2.getOrderCode());
            }
        }
    }
}
