package com.bizpos.service.impl;

import com.bizpos.dto.CreateOrderRequest;
import com.bizpos.dto.OrderItemRequest;
import com.bizpos.entity.Customer;
import com.bizpos.entity.Order;
import com.bizpos.entity.OrderItem;
import com.bizpos.entity.Product;
import com.bizpos.entity.ProductVariant;
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
    private final com.bizpos.service.StockMovementService stockMovementService;
    private final com.bizpos.repository.ProductVariantRepository productVariantRepository;
    private final com.bizpos.repository.OrderReturnRepository orderReturnRepository;

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

        // Sinh mã đơn hàng trước để dùng làm referenceCode cho StockMovement
        String orderCode = generateUniqueOrderCode();
        order.setOrderCode(orderCode);

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Sắp xếp items theo identifier tăng dần để phòng chống triệt để nguy cơ DEADLOCK giữa các transaction đồng thời
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getVariantId() != null ? i.getVariantId() : (i.getProductId() != null ? i.getProductId() : 0L)))
                .toList();

        for (OrderItemRequest itemReq : sortedItems) {
            if (itemReq.getProductId() == null && itemReq.getVariantId() == null) {
                throw new IllegalArgumentException("Vui lòng cung cấp productId hoặc variantId cho từng mục hàng!");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng mua cho từng sản phẩm phải lớn hơn 0!");
            }

            int requestedQty = itemReq.getQuantity();
            Product product;
            com.bizpos.entity.ProductVariant variant = null;
            BigDecimal unitPrice;
            int currentStock;
            int newStock;

            // 2. Ưu tiên xử lý theo Variant nếu có variantId
            if (itemReq.getVariantId() != null) {
                variant = productVariantRepository.findByIdWithLock(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm với ID: " + itemReq.getVariantId()));
                product = variant.getProduct();

                currentStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                if (requestedQty > currentStock) {
                    throw new com.bizpos.exception.InsufficientStockException(
                            "Biến thể '" + product.getName() + " (Size: " + variant.getSize() + ", Màu: " + variant.getColor() + ")' không đủ tồn kho (Hiện có: " + currentStock + ", yêu cầu: " + requestedQty + ")!");
                }

                newStock = currentStock - requestedQty;
                variant.setStockQuantity(newStock);
                productVariantRepository.save(variant);

                // Đồng bộ tồn kho trên Product cha nếu cần
                if (product.getStockQuantity() != null && product.getStockQuantity() >= requestedQty) {
                    product.setStockQuantity(product.getStockQuantity() - requestedQty);
                    productRepository.save(product);
                }

                unitPrice = variant.getPrice() != null ? variant.getPrice() : product.getPrice();

                // Ghi nhật ký kho với Variant
                stockMovementService.recordMovement(
                        product,
                        variant,
                        com.bizpos.entity.MovementType.SALE,
                        requestedQty,
                        currentStock,
                        newStock,
                        orderCode,
                        "Xuất kho bán hàng biến thể [" + variant.getSku() + "] theo đơn " + orderCode,
                        getCurrentUsername()
                );
            } else {
                // Fallback theo productId để tương thích ngược
                product = productRepository.findByIdWithLock(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

                currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                if (requestedQty > currentStock) {
                    throw new com.bizpos.exception.InsufficientStockException(
                            "Sản phẩm '" + product.getName() + "' (Mã: " + product.getCode() + 
                            ") không đủ số lượng tồn kho (Tồn kho hiện tại: " + currentStock + ", yêu cầu: " + requestedQty + ")!");
                }

                newStock = currentStock - requestedQty;
                product.setStockQuantity(newStock);
                productRepository.save(product);

                // Tìm variant liên kết để đồng bộ
                java.util.List<com.bizpos.entity.ProductVariant> vars = productVariantRepository.findByProductId(product.getId());
                if (!vars.isEmpty()) {
                    variant = vars.get(0);
                    if (variant.getStockQuantity() != null && variant.getStockQuantity() >= requestedQty) {
                        variant.setStockQuantity(variant.getStockQuantity() - requestedQty);
                        productVariantRepository.save(variant);
                    }
                }

                unitPrice = product.getPrice();

                // Ghi nhật ký kho
                stockMovementService.recordMovement(
                        product,
                        variant,
                        com.bizpos.entity.MovementType.SALE,
                        requestedQty,
                        currentStock,
                        newStock,
                        orderCode,
                        "Xuất kho bán hàng theo đơn " + orderCode,
                        getCurrentUsername()
                );
            }

            // 4. Tạo từng OrderItem gồm tên sản phẩm, đơn giá, số lượng và thành tiền
            int quantity = itemReq.getQuantity();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .variant(variant)
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

        BigDecimal subtotal = totalAmount;
        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Số tiền chiết khấu không được âm!");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Số tiền chiết khấu (" + discount + ") không được vượt quá tổng tiền hàng (" + subtotal + ")!");
        }
        BigDecimal finalTotal = subtotal.subtract(discount);

        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setTotalAmount(finalTotal);
        applyPaymentDetails(order, request, finalTotal);

        // Lưu Order cùng các OrderItem (CascadeType.ALL sẽ tự động lưu các OrderItem)
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
    @com.bizpos.aspect.Auditable(action = "CANCEL_ORDER", entity = "Order")
    public Order cancelOrder(Long id, String reason) {
        // 0. KHÓA ĐƠN HÀNG TRƯỚC HẾT (Pessimistic Write Lock) để thống nhất thứ tự: Order -> Products (ID tăng dần)
        Order order = orderRepository.findByIdWithLock(id)
                .or(() -> orderRepository.findByIdWithDetails(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));

        if (order.getStatus() == com.bizpos.enums.OrderStatus.CANCELLED) {
            throw new IllegalStateException("Đơn hàng này đã bị hủy trước đó!");
        }
        if (order.getStatus() == com.bizpos.enums.OrderStatus.RETURNED) {
            throw new IllegalStateException("Đơn hàng này đã được hoàn trả toàn bộ, không thể hủy đơn!");
        }

        // 1. Gom tất cả productIds và variantIds của đơn hàng, sắp xếp tăng dần theo ID để chống Deadlock
        java.util.Set<Long> productIds = new java.util.TreeSet<>();
        java.util.Set<Long> variantIds = new java.util.TreeSet<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    productIds.add(item.getProduct().getId());
                }
                if (item.getVariant() != null) {
                    variantIds.add(item.getVariant().getId());
                }
            }
        }

        // 2. Khóa các sản phẩm và biến thể theo thứ tự ID tăng dần
        java.util.Map<Long, Product> lockedProducts = new java.util.HashMap<>();
        for (Long pid : productIds) {
            Product p = productRepository.findByIdWithLock(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + pid));
            lockedProducts.put(pid, p);
        }

        java.util.Map<Long, com.bizpos.entity.ProductVariant> lockedVariants = new java.util.HashMap<>();
        for (Long vid : variantIds) {
            com.bizpos.entity.ProductVariant v = productVariantRepository.findByIdWithLock(vid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + vid));
            lockedVariants.put(vid, v);
        }

        // 3. Hoàn tồn kho cho các sản phẩm chưa bị đổi/trả (Tránh hoàn trùng kho nếu thu ngân đã trả hàng trước đó)
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                int alreadyReturned = 0;
                if (item.getId() != null) {
                    alreadyReturned = orderReturnRepository.countReturnedQuantityByOrderItem(item.getId());
                }
                int remainingToRestock = item.getQuantity() - alreadyReturned;
                if (remainingToRestock <= 0) {
                    continue; // Món này đã được thu ngân xử lý trả và hoàn kho trước đó
                }

                if (item.getVariant() != null) {
                    com.bizpos.entity.ProductVariant variant = lockedVariants.get(item.getVariant().getId());
                    int currentStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                    int newStock = currentStock + remainingToRestock;
                    variant.setStockQuantity(newStock);
                    productVariantRepository.save(variant);

                    Product prod = lockedProducts.get(variant.getProduct().getId());
                    if (prod != null) {
                        int pStock = prod.getStockQuantity() != null ? prod.getStockQuantity() : 0;
                        prod.setStockQuantity(pStock + remainingToRestock);
                        productRepository.save(prod);
                    }

                    stockMovementService.recordMovement(
                            prod,
                            variant,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            currentStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do hủy đơn hàng " + order.getOrderCode() + (reason != null ? " (" + reason + ")" : ""),
                            getCurrentUsername()
                    );
                } else if (item.getProduct() != null) {
                    Product prod = lockedProducts.get(item.getProduct().getId());
                    int currentStock = prod.getStockQuantity() != null ? prod.getStockQuantity() : 0;
                    int newStock = currentStock + remainingToRestock;
                    prod.setStockQuantity(newStock);
                    productRepository.save(prod);

                    stockMovementService.recordMovement(
                            prod,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            currentStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do hủy đơn hàng " + order.getOrderCode() + (reason != null ? " (" + reason + ")" : ""),
                            getCurrentUsername()
                    );
                }
            }
        }

        order.setStatus(com.bizpos.enums.OrderStatus.CANCELLED);
        order.setPaymentStatus(com.bizpos.enums.PaymentStatus.REFUNDED);
        if (reason != null && !reason.trim().isEmpty()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | [HỦY ĐƠN]: " : "[HỦY ĐƠN]: ") + reason.trim());
        }
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "UPDATE_ORDER", entity = "Order")
    public Order updateOrder(Long id, CreateOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải chứa ít nhất 1 sản phẩm!");
        }

        // 0. KHÓA ĐƠN HÀNG TRƯỚC HẾT (Pessimistic Write Lock)
        Order order = orderRepository.findByIdWithLock(id)
                .or(() -> orderRepository.findByIdWithDetails(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));

        if (order.getStatus() == com.bizpos.enums.OrderStatus.CANCELLED) {
            throw new IllegalStateException("Không thể chỉnh sửa đơn hàng đã bị hủy!");
        }

        // 1. Gom tất cả productIds và variantIds của CẢ dòng cũ LẪN dòng mới, sắp xếp tăng dần theo ID để PESSIMISTIC LOCK chống DEADLOCK
        java.util.Set<Long> allProductIds = new java.util.TreeSet<>();
        java.util.Set<Long> allVariantIds = new java.util.TreeSet<>();

        if (order.getItems() != null) {
            for (OrderItem oi : order.getItems()) {
                if (oi.getProduct() != null) allProductIds.add(oi.getProduct().getId());
                if (oi.getVariant() != null) allVariantIds.add(oi.getVariant().getId());
            }
        }

        for (OrderItemRequest reqItem : request.getItems()) {
            if (reqItem.getProductId() != null) allProductIds.add(reqItem.getProductId());
            if (reqItem.getVariantId() != null) allVariantIds.add(reqItem.getVariantId());
        }

        // 2. Khóa các sản phẩm và biến thể theo thứ tự ID tăng dần
        java.util.Map<Long, Product> lockedProducts = new java.util.HashMap<>();
        for (Long pid : allProductIds) {
            Product p = productRepository.findByIdWithLock(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + pid));
            lockedProducts.put(pid, p);
        }

        java.util.Map<Long, com.bizpos.entity.ProductVariant> lockedVariants = new java.util.HashMap<>();
        for (Long vid : allVariantIds) {
            com.bizpos.entity.ProductVariant v = productVariantRepository.findByIdWithLock(vid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + vid));
            lockedVariants.put(vid, v);
        }

        // 3. Hoàn trả tồn kho cho các OrderItem cũ (có trừ đi số lượng đã bị đổi/trả nếu có)
        if (order.getItems() != null) {
            for (OrderItem oldItem : order.getItems()) {
                int alreadyReturned = 0;
                if (oldItem.getId() != null) {
                    alreadyReturned = orderReturnRepository.countReturnedQuantityByOrderItem(oldItem.getId());
                }
                int remainingToRestock = oldItem.getQuantity() - alreadyReturned;
                if (remainingToRestock <= 0) continue;

                if (oldItem.getVariant() != null) {
                    com.bizpos.entity.ProductVariant variant = lockedVariants.get(oldItem.getVariant().getId());
                    int curStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                    int newStock = curStock + remainingToRestock;
                    variant.setStockQuantity(newStock);
                    productVariantRepository.save(variant);

                    Product prod = lockedProducts.get(variant.getProduct().getId());
                    if (prod != null) {
                        int pStock = prod.getStockQuantity() != null ? prod.getStockQuantity() : 0;
                        prod.setStockQuantity(pStock + remainingToRestock);
                        productRepository.save(prod);
                    }

                    stockMovementService.recordMovement(
                            prod,
                            variant,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            curStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do cập nhật đơn hàng " + order.getOrderCode(),
                            getCurrentUsername()
                    );
                } else if (oldItem.getProduct() != null) {
                    Product oldProduct = lockedProducts.get(oldItem.getProduct().getId());
                    int currentStock = oldProduct.getStockQuantity() != null ? oldProduct.getStockQuantity() : 0;
                    int newStock = currentStock + remainingToRestock;
                    oldProduct.setStockQuantity(newStock);
                    productRepository.save(oldProduct);

                    stockMovementService.recordMovement(
                            oldProduct,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            currentStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do cập nhật đơn hàng " + order.getOrderCode(),
                            getCurrentUsername()
                    );
                }
            }
        }

        // 4. Cập nhật khách hàng và ghi chú
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + request.getCustomerId()));
        }
        order.setCustomer(customer);
        order.setNote(request.getNote() != null ? request.getNote().trim() : null);

        // 5. Xóa danh sách OrderItem cũ khỏi đơn
        order.getItems().clear();

        // 6. Trừ tồn kho và thêm OrderItem mới
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getVariantId() != null ? i.getVariantId() : (i.getProductId() != null ? i.getProductId() : 0L)))
                .toList();

        for (OrderItemRequest itemReq : sortedItems) {
            ProductVariant variant = null;
            Product product = null;
            if (itemReq.getVariantId() != null) {
                variant = lockedVariants.get(itemReq.getVariantId());
                product = variant.getProduct();
            } else if (itemReq.getProductId() != null) {
                product = lockedProducts.get(itemReq.getProductId());
            }

            int requestedQty = itemReq.getQuantity();
            int currentStock = variant != null ? (variant.getStockQuantity() != null ? variant.getStockQuantity() : 0)
                                               : (product.getStockQuantity() != null ? product.getStockQuantity() : 0);

            if (requestedQty > currentStock) {
                throw new com.bizpos.exception.InsufficientStockException(
                        "Sản phẩm '" + product.getName() +
                        "' không đủ số lượng tồn kho (Tồn kho hiện tại: " + currentStock + ", yêu cầu: " + requestedQty + ")!");
            }

            int newStock = currentStock - requestedQty;
            if (variant != null) {
                variant.setStockQuantity(newStock);
                productVariantRepository.save(variant);

                int pStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                product.setStockQuantity(pStock - requestedQty);
                productRepository.save(product);

                stockMovementService.recordMovement(
                        product,
                        variant,
                        com.bizpos.entity.MovementType.SALE,
                        requestedQty,
                        currentStock,
                        newStock,
                        order.getOrderCode(),
                        "Xuất kho bán hàng (cập nhật đơn hàng) " + order.getOrderCode(),
                        getCurrentUsername()
                );
            } else {
                product.setStockQuantity(newStock);
                productRepository.save(product);

                stockMovementService.recordMovement(
                        product,
                        com.bizpos.entity.MovementType.SALE,
                        requestedQty,
                        currentStock,
                        newStock,
                        order.getOrderCode(),
                        "Xuất kho bán hàng (cập nhật đơn hàng) " + order.getOrderCode(),
                        getCurrentUsername()
                );
            }

            BigDecimal unitPrice = (variant != null && variant.getPrice() != null)
                    ? variant.getPrice()
                    : product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQty));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .variant(variant)
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(requestedQty)
                    .lineTotal(lineTotal)
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(lineTotal);
        }

        BigDecimal subtotal = totalAmount;
        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Số tiền chiết khấu không được âm!");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Số tiền chiết khấu (" + discount + ") không được vượt quá tổng tiền hàng (" + subtotal + ")!");
        }
        BigDecimal finalTotal = subtotal.subtract(discount);

        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setTotalAmount(finalTotal);
        applyPaymentDetails(order, request, finalTotal);

        return orderRepository.save(order);
    }

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "DELETE_ORDER", entity = "Order")
    public void deleteOrder(Long id) {
        // 0. KHÓA ĐƠN HÀNG TRƯỚC HẾT (Pessimistic Write Lock) để thống nhất thứ tự: Order -> Products (ID tăng dần)
        Order order = orderRepository.findByIdWithLock(id)
                .or(() -> orderRepository.findByIdWithDetails(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));

        // Nếu đơn hàng chưa bị hủy thì mới cần hoàn kho (nếu đã bị CANCELLED thì kho đã hoàn lúc hủy)
        if (order.getStatus() != com.bizpos.enums.OrderStatus.CANCELLED && order.getItems() != null) {
            java.util.Set<Long> productIds = new java.util.TreeSet<>();
            java.util.Set<Long> variantIds = new java.util.TreeSet<>();
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    productIds.add(item.getProduct().getId());
                }
                if (item.getVariant() != null) {
                    variantIds.add(item.getVariant().getId());
                }
            }

            java.util.Map<Long, Product> lockedProducts = new java.util.HashMap<>();
            for (Long pid : productIds) {
                Product p = productRepository.findByIdWithLock(pid)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + pid));
                lockedProducts.put(pid, p);
            }

            java.util.Map<Long, com.bizpos.entity.ProductVariant> lockedVariants = new java.util.HashMap<>();
            for (Long vid : variantIds) {
                com.bizpos.entity.ProductVariant v = productVariantRepository.findByIdWithLock(vid)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + vid));
                lockedVariants.put(vid, v);
            }

            for (OrderItem item : order.getItems()) {
                int alreadyReturned = 0;
                if (item.getId() != null) {
                    alreadyReturned = orderReturnRepository.countReturnedQuantityByOrderItem(item.getId());
                }
                int remainingToRestock = item.getQuantity() - alreadyReturned;
                if (remainingToRestock <= 0) {
                    continue;
                }

                if (item.getVariant() != null) {
                    com.bizpos.entity.ProductVariant variant = lockedVariants.get(item.getVariant().getId());
                    int currentStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                    int newStock = currentStock + remainingToRestock;
                    variant.setStockQuantity(newStock);
                    productVariantRepository.save(variant);

                    Product prod = lockedProducts.get(variant.getProduct().getId());
                    if (prod != null) {
                        int pStock = prod.getStockQuantity() != null ? prod.getStockQuantity() : 0;
                        prod.setStockQuantity(pStock + remainingToRestock);
                        productRepository.save(prod);
                    }

                    stockMovementService.recordMovement(
                            prod,
                            variant,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            currentStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do xóa đơn hàng " + order.getOrderCode(),
                            getCurrentUsername()
                    );
                } else if (item.getProduct() != null) {
                    Product prod = lockedProducts.get(item.getProduct().getId());
                    int currentStock = prod.getStockQuantity() != null ? prod.getStockQuantity() : 0;
                    int newStock = currentStock + remainingToRestock;
                    prod.setStockQuantity(newStock);
                    productRepository.save(prod);

                    stockMovementService.recordMovement(
                            prod,
                            com.bizpos.entity.MovementType.RETURN,
                            remainingToRestock,
                            currentStock,
                            newStock,
                            order.getOrderCode(),
                            "Hoàn kho do xóa đơn hàng " + order.getOrderCode(),
                            getCurrentUsername()
                    );
                }
            }
        }

        orderRepository.delete(order);
    }

    private String getCurrentUsername() {
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
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

    /**
     * Áp dụng thông tin thanh toán (Tiền mặt / Chuyển khoản) cho đơn hàng
     */
    private void applyPaymentDetails(Order order, CreateOrderRequest request, BigDecimal totalAmount) {
        com.bizpos.enums.PaymentMethod method = request.getPaymentMethod() != null
                ? request.getPaymentMethod()
                : (order.getPaymentMethod() != null ? order.getPaymentMethod() : com.bizpos.enums.PaymentMethod.CASH);
        order.setPaymentMethod(method);
        order.setPaymentStatus(com.bizpos.enums.PaymentStatus.COMPLETED);
        if (request.getPaymentNote() != null) {
            order.setPaymentNote(request.getPaymentNote().trim());
        }

        if (method == com.bizpos.enums.PaymentMethod.BANK_TRANSFER) {
            order.setAmountPaid(totalAmount);
            order.setChangeAmount(BigDecimal.ZERO);
        } else {
            // Tiền mặt (CASH)
            BigDecimal amountPaid = request.getAmountPaid();
            if (amountPaid == null) {
                // Tương thích ngược: Nếu client không truyền amountPaid, mặc định khách đưa đủ
                amountPaid = totalAmount;
            } else if (amountPaid.compareTo(totalAmount) < 0) {
                throw new IllegalArgumentException(
                        "Số tiền khách đưa (" + amountPaid + ") không đủ thanh toán tổng đơn (" + totalAmount + ")!"
                );
            }
            order.setAmountPaid(amountPaid);
            order.setChangeAmount(amountPaid.subtract(totalAmount));
        }
    }
}
