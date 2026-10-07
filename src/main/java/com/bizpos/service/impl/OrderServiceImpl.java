package com.bizpos.service.impl;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderItem;
import com.bizpos.entity.Product;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    /**
     * Tạo đơn hàng mới kết hợp Customer, Product, Order và OrderItem
     * Toàn bộ phương thức chạy trong Transaction để đảm bảo tính toàn vẹn (ACID).
     */
    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải chứa ít nhất 1 sản phẩm!");
        }

        // 1. Nếu có customerId, kiểm tra khách hàng có tồn tại
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + request.getCustomerId()));
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDateTime.now());
        order.setNote(request.getNote() != null ? request.getNote().trim() : null);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Sắp xếp items theo productId tăng dần để phòng chống triệt để nguy cơ DEADLOCK giữa các transaction đồng thời
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::getProductId))
                .toList();

        for (OrderItemRequest itemReq : sortedItems) {
            if (itemReq.getProductId() == null) {
                throw new IllegalArgumentException("Vui lòng cung cấp productId cho từng mục hàng!");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng mua cho từng sản phẩm phải lớn hơn 0!");
            }

            // 2. Lấy từng Product theo productId với Pessimistic Lock (SELECT ... FOR UPDATE)
            Product product = productRepository.findByIdWithLock(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

            // KIỂM TRA VÀ TRỪ TỒN KHO:
            int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            int requestedQty = itemReq.getQuantity();

            if (requestedQty > currentStock) {
                throw new com.bizpos.exception.InsufficientStockException(
                        "Sản phẩm '" + product.getName() + "' (Mã: " + product.getCode() + 
                        ") không đủ số lượng tồn kho (Tồn kho hiện tại: " + currentStock + ", yêu cầu: " + requestedQty + ")!");
            }

            product.setStockQuantity(currentStock - requestedQty);
            productRepository.save(product);

            // 3. Lấy giá từ product.price; tuyệt đối không lấy giá từ request
            BigDecimal unitPrice = product.getPrice();

            // 4. Tạo từng OrderItem gồm tên sản phẩm, đơn giá, số lượng và thành tiền
            int quantity = itemReq.getQuantity();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build();

            // Thêm item vào order (thiết lập quan hệ hai chiều)
            order.addItem(orderItem);

            // 5. Tính tổng tiền của toàn bộ đơn
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);

        // 6. Tạo orderCode duy nhất
        order.setOrderCode(generateUniqueOrderCode());

        // 7. Lưu Order cùng các OrderItem (CascadeType.ALL sẽ tự động lưu các OrderItem)
        return orderRepository.save(order);
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    @Override
    public Order getOrderById(Long id) {
        return orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    @Override
    public Order getOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));
    }

    @Override
    @Transactional
    public Order updateOrder(Long id, CreateOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải chứa ít nhất 1 sản phẩm!");
        }

        // 1. Tìm đơn theo id; không có thì báo lỗi
        Order order = getOrderById(id);

        // 2. Cập nhật khách hàng và ghi chú
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + request.getCustomerId()));
        }
        order.setCustomer(customer);
        order.setNote(request.getNote() != null ? request.getNote().trim() : null);

        // 3. Hoàn trả lại số lượng tồn kho của các OrderItem cũ trước khi thay thế
        if (order.getItems() != null) {
            for (OrderItem oldItem : order.getItems()) {
                if (oldItem.getProduct() != null) {
                    Product oldProduct = oldItem.getProduct();
                    int currentStock = oldProduct.getStockQuantity() != null ? oldProduct.getStockQuantity() : 0;
                    oldProduct.setStockQuantity(currentStock + oldItem.getQuantity());
                    productRepository.save(oldProduct);
                }
            }
        }

        // 4. Xóa danh sách OrderItem cũ khỏi đơn (orphanRemoval = true sẽ xóa các dòng chi tiết cũ khỏi DB)
        order.getItems().clear();

        // 5. Tạo lại các OrderItem mới từ request (sắp xếp items theo productId tăng dần để chống Deadlock)
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::getProductId))
                .toList();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : sortedItems) {
            if (itemReq.getProductId() == null) {
                throw new IllegalArgumentException("Vui lòng cung cấp productId cho từng mục hàng!");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng mua cho từng sản phẩm phải lớn hơn 0!");
            }

            Product product = productRepository.findByIdWithLock(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

            // KIỂM TRA VÀ TRỪ TỒN KHO CHO ĐƠN CẬP NHẬT:
            int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            int requestedQty = itemReq.getQuantity();

            if (requestedQty > currentStock) {
                throw new com.bizpos.exception.InsufficientStockException(
                        "Sản phẩm '" + product.getName() + "' (Mã: " + product.getCode() + 
                        ") không đủ số lượng tồn kho (Tồn kho hiện tại: " + currentStock + ", yêu cầu: " + requestedQty + ")!");
            }

            product.setStockQuantity(currentStock - requestedQty);
            productRepository.save(product);

            BigDecimal unitPrice = product.getPrice();
            int quantity = itemReq.getQuantity();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build();

            order.addItem(orderItem);

            // 6. Tính lại totalAmount
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);

        // 7. Lưu trong @Transactional
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = getOrderById(id);

        // Hoàn trả tồn kho cho tất cả các sản phẩm trong đơn khi đơn bị xóa
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                    product.setStockQuantity(currentStock + item.getQuantity());
                    productRepository.save(product);
                }
            }
        }

        orderRepository.delete(order);
    }

    /**
     * Sinh mã đơn hàng duy nhất có định dạng: ORD-yyyyMMddHHmmss-XXX
     */
    private String generateUniqueOrderCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderCode;
        do {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            orderCode = "ORD-" + timestamp + "-" + randomSuffix;
        } while (orderRepository.existsByOrderCode(orderCode));

        return orderCode;
    }
}
