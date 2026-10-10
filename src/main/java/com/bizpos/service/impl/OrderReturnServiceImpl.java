package com.bizpos.service.impl;

import com.bizpos.aspect.Auditable;
import com.bizpos.dto.*;
import com.bizpos.entity.*;
import com.bizpos.enums.ReturnReason;
import com.bizpos.enums.ReturnStatus;
import com.bizpos.enums.ReturnType;
import com.bizpos.exception.InsufficientStockException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.OrderReturnRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.OrderReturnService;
import com.bizpos.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    public static final int RETURN_POLICY_DAYS = 7;

    private final OrderReturnRepository orderReturnRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final com.bizpos.repository.ProductVariantRepository productVariantRepository;

    @Override
    @Transactional
    @Auditable(action = "PROCESS_RETURN", entity = "OrderReturn")
    public OrderReturnResponse processReturn(OrderReturnRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin yêu cầu đổi / trả hàng không được để trống!");
        }

        String orderCode = request.getOrderCode();
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng cung cấp mã đơn hàng gốc!");
        }

        // 0. PESSIMISTIC LOCK (SELECT FOR UPDATE) trên đơn hàng gốc để tuần tự hóa và chống Race Condition khi nhiều thu ngân cùng xử lý đổi/trả cho một hóa đơn
        Order order = orderRepository.findByOrderCodeWithLock(orderCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        // 1. Kiểm tra chính sách hạn đổi trả (7 ngày)
        LocalDate purchaseDate = order.getOrderDate().toLocalDate();
        long daysSincePurchase = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        if (daysSincePurchase > RETURN_POLICY_DAYS) {
            throw new IllegalArgumentException("Đơn hàng '" + orderCode + "' đã mua cách đây " + daysSincePurchase + 
                    " ngày, vượt quá thời hạn cho phép đổi trả (" + RETURN_POLICY_DAYS + " ngày)!");
        }

        if (request.getReturnItems() == null || request.getReturnItems().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất 1 sản phẩm cần trả lại!");
        }

        // 2. Tạo mã phiếu đổi trả duy nhất
        String returnCode = generateUniqueReturnCode();
        String currentUser = getCurrentUsername();

        // 3. Gom tất cả productIds và variantIds rồi sắp xếp tăng dần để PESSIMISTIC LOCK phòng chống DEADLOCK
        Set<Long> allProductIds = new TreeSet<>();
        Set<Long> allVariantIds = new TreeSet<>();

        for (ReturnItemRequest ri : request.getReturnItems()) {
            if ((ri.getOrderItemId() == null && ri.getProductId() == null && ri.getVariantId() == null) || ri.getQuantity() == null || ri.getQuantity() <= 0) {
                throw new IllegalArgumentException("Thông tin sản phẩm trả lại không hợp lệ!");
            }
            OrderItem match = null;
            if (ri.getOrderItemId() != null) {
                match = order.getItems().stream()
                        .filter(item -> item.getId().equals(ri.getOrderItemId()))
                        .findFirst().orElse(null);
            }
            if (match == null && ri.getVariantId() != null) {
                match = order.getItems().stream()
                        .filter(item -> item.getVariant() != null && item.getVariant().getId().equals(ri.getVariantId()))
                        .findFirst().orElse(null);
            }
            if (match == null && ri.getProductId() != null) {
                match = order.getItems().stream()
                        .filter(item -> item.getProduct().getId().equals(ri.getProductId()))
                        .findFirst().orElse(null);
            }
            if (match == null) {
                throw new IllegalArgumentException("Sản phẩm yêu cầu trả lại không có trong đơn hàng gốc " + orderCode + "!");
            }
            allProductIds.add(match.getProduct().getId());
            if (match.getVariant() != null) {
                allVariantIds.add(match.getVariant().getId());
            }
        }

        if (request.getExchangeItems() != null) {
            for (ExchangeItemRequest ei : request.getExchangeItems()) {
                if ((ei.getProductId() == null && ei.getVariantId() == null) || ei.getQuantity() == null || ei.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Thông tin sản phẩm đổi lấy mới không hợp lệ!");
                }
                if (ei.getVariantId() != null) {
                    allVariantIds.add(ei.getVariantId());
                    ProductVariant v = productVariantRepository.findById(ei.getVariantId())
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + ei.getVariantId()));
                    allProductIds.add(v.getProduct().getId());
                } else {
                    allProductIds.add(ei.getProductId());
                }
            }
        }

        // Lock tất cả sản phẩm & biến thể liên quan theo thứ tự ID tăng dần
        Map<Long, Product> lockedProductMap = new HashMap<>();
        for (Long pid : allProductIds) {
            Product p = productRepository.findByIdWithLock(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + pid));
            lockedProductMap.put(pid, p);
        }

        Map<Long, ProductVariant> lockedVariantMap = new HashMap<>();
        for (Long vid : allVariantIds) {
            ProductVariant pv = productVariantRepository.findByIdWithLock(vid)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + vid));
            lockedVariantMap.put(vid, pv);
        }

        // 4. Khởi tạo đối tượng OrderReturn
        boolean hasExchangeItems = request.getExchangeItems() != null && !request.getExchangeItems().isEmpty();
        ReturnType resolvedType = hasExchangeItems ? ReturnType.EXCHANGE : ReturnType.RETURN_ONLY;

        OrderReturn orderReturn = OrderReturn.builder()
                .returnCode(returnCode)
                .order(order)
                .returnType(resolvedType)
                .status(ReturnStatus.COMPLETED)
                .reason(request.getReason())
                .note(request.getNote() != null ? request.getNote().trim() : null)
                .performedBy(currentUser)
                .build();

        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        // 5. Xử lý các món trả lại (RETURN ITEMS)
        for (ReturnItemRequest ri : request.getReturnItems()) {
            OrderItem originalItem = null;
            if (ri.getOrderItemId() != null) {
                originalItem = order.getItems().stream()
                        .filter(item -> item.getId().equals(ri.getOrderItemId()))
                        .findFirst().orElse(null);
            }
            if (originalItem == null && ri.getVariantId() != null) {
                originalItem = order.getItems().stream()
                        .filter(item -> item.getVariant() != null && item.getVariant().getId().equals(ri.getVariantId()))
                        .findFirst().orElse(null);
            }
            if (originalItem == null && ri.getProductId() != null) {
                originalItem = order.getItems().stream()
                        .filter(item -> item.getProduct().getId().equals(ri.getProductId()))
                        .findFirst().orElse(null);
            }
            if (originalItem == null) {
                throw new IllegalArgumentException("Dòng sản phẩm yêu cầu trả lại không có trong đơn hàng gốc " + orderCode + "!");
            }

            int purchasedQty = originalItem.getQuantity();
            int alreadyReturnedQty = orderReturnRepository.countReturnedQuantityByOrderItem(originalItem.getId());
            int remainingAllowedQty = purchasedQty - alreadyReturnedQty;

            if (ri.getQuantity() > remainingAllowedQty) {
                throw new IllegalArgumentException("Sản phẩm '" + originalItem.getProductName() + 
                        "' chỉ còn được trả tối đa " + remainingAllowedQty + " món (đã mua: " + purchasedQty + 
                        ", đã trả trước đó: " + alreadyReturnedQty + "), yêu cầu trả: " + ri.getQuantity() + "!");
            }

            Product product = lockedProductMap.get(originalItem.getProduct().getId());
            ProductVariant variant = originalItem.getVariant() != null ? lockedVariantMap.get(originalItem.getVariant().getId()) : null;
            int returnQty = ri.getQuantity();

            ReturnReason itemReason = ri.getReason() != null ? ri.getReason() : request.getReason();

            // Tăng tồn kho
            if (variant != null) {
                int oldVarStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                int newVarStock = oldVarStock + returnQty;
                variant.setStockQuantity(newVarStock);
                productVariantRepository.save(variant);

                int oldProdStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                int newProdStock = oldProdStock + returnQty;
                product.setStockQuantity(newProdStock);
                productRepository.save(product);

                stockMovementService.recordMovement(
                        product,
                        variant,
                        MovementType.RETURN,
                        returnQty,
                        oldVarStock,
                        newVarStock,
                        returnCode,
                        "Khách trả hàng biến thể [" + variant.getSku() + "] [Đơn: " + orderCode + "] - Lý do: " + itemReason.getDescription(),
                        currentUser
                );
            } else {
                int oldStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                int newStock = oldStock + returnQty;
                product.setStockQuantity(newStock);
                productRepository.save(product);

                List<ProductVariant> vars = productVariantRepository.findByProductId(product.getId());
                if (!vars.isEmpty()) {
                    ProductVariant firstVar = vars.get(0);
                    firstVar.setStockQuantity((firstVar.getStockQuantity() != null ? firstVar.getStockQuantity() : 0) + returnQty);
                    productVariantRepository.save(firstVar);
                }

                stockMovementService.recordMovement(
                        product,
                        MovementType.RETURN,
                        returnQty,
                        oldStock,
                        newStock,
                        returnCode,
                        "Khách trả hàng [Đơn: " + orderCode + "] - Lý do: " + itemReason.getDescription(),
                        currentUser
                );
            }

            BigDecimal unitPrice = originalItem.getUnitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(returnQty));
            totalRefundAmount = totalRefundAmount.add(lineTotal);

            OrderReturnItem returnItem = OrderReturnItem.builder()
                    .orderReturn(orderReturn)
                    .orderItem(originalItem)
                    .product(product)
                    .variant(variant)
                    .productCode(product.getCode())
                    .productName(originalItem.getProductName())
                    .size(variant != null && variant.getSize() != null ? variant.getSize() : (originalItem.getVariant() != null ? originalItem.getVariant().getSize() : product.getSize()))
                    .color(variant != null && variant.getColor() != null ? variant.getColor() : (originalItem.getVariant() != null ? originalItem.getVariant().getColor() : product.getColor()))
                    .quantity(returnQty)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .reason(itemReason)
                    .note(ri.getNote() != null ? ri.getNote().trim() : null)
                    .build();

            orderReturn.addReturnItem(returnItem);
        }

        // 6. Xử lý các món đổi lấy mới (EXCHANGE ITEMS nếu có)
        BigDecimal totalExchangeAmount = BigDecimal.ZERO;
        if (hasExchangeItems) {
            for (ExchangeItemRequest ei : request.getExchangeItems()) {
                int requestedQty = ei.getQuantity();
                Product product;
                ProductVariant variant = null;
                BigDecimal unitPrice;

                if (ei.getVariantId() != null) {
                    variant = lockedVariantMap.get(ei.getVariantId());
                    product = lockedProductMap.get(variant.getProduct().getId());

                    int oldStock = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                    if (requestedQty > oldStock) {
                        throw new InsufficientStockException("Biến thể '" + product.getName() + 
                                "' (SKU: " + variant.getSku() + ", Size: " + variant.getSize() + ", Màu: " + variant.getColor() + 
                                ") không đủ tồn kho (Hiện có: " + oldStock + ", yêu cầu: " + requestedQty + ")!");
                    }

                    int newStock = oldStock - requestedQty;
                    variant.setStockQuantity(newStock);
                    productVariantRepository.save(variant);

                    if (product.getStockQuantity() != null && product.getStockQuantity() >= requestedQty) {
                        product.setStockQuantity(product.getStockQuantity() - requestedQty);
                        productRepository.save(product);
                    }

                    stockMovementService.recordMovement(
                            product,
                            variant,
                            MovementType.SALE,
                            requestedQty,
                            oldStock,
                            newStock,
                            returnCode,
                            "Khách đổi lấy biến thể mới [" + variant.getSku() + "] theo phiếu " + returnCode + " (Đơn gốc: " + orderCode + ")",
                            currentUser
                    );

                    unitPrice = variant.getPrice() != null ? variant.getPrice() : product.getPrice();
                } else {
                    product = lockedProductMap.get(ei.getProductId());
                    int oldStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
                    if (requestedQty > oldStock) {
                        throw new InsufficientStockException("Sản phẩm đổi mới '" + product.getName() + 
                                "' (Mã: " + product.getCode() + ") không đủ tồn kho (Hiện có: " + oldStock + ", yêu cầu: " + requestedQty + ")!");
                    }

                    int newStock = oldStock - requestedQty;
                    product.setStockQuantity(newStock);
                    productRepository.save(product);

                    List<ProductVariant> vars = productVariantRepository.findByProductId(product.getId());
                    if (!vars.isEmpty()) {
                        variant = vars.get(0);
                        if (variant.getStockQuantity() != null && variant.getStockQuantity() >= requestedQty) {
                            variant.setStockQuantity(variant.getStockQuantity() - requestedQty);
                            productVariantRepository.save(variant);
                        }
                    }

                    stockMovementService.recordMovement(
                            product,
                            MovementType.SALE,
                            requestedQty,
                            oldStock,
                            newStock,
                            returnCode,
                            "Khách đổi lấy món mới theo phiếu " + returnCode + " (Đơn gốc: " + orderCode + ")",
                            currentUser
                    );

                    unitPrice = product.getPrice();
                }

                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
                totalExchangeAmount = totalExchangeAmount.add(lineTotal);

                OrderExchangeItem exchangeItem = OrderExchangeItem.builder()
                        .product(product)
                        .variant(variant)
                        .productCode(product.getCode())
                        .productName(product.getName())
                        .size(variant != null && variant.getSize() != null ? variant.getSize() : product.getSize())
                        .color(variant != null && variant.getColor() != null ? variant.getColor() : product.getColor())
                        .quantity(requestedQty)
                        .unitPrice(unitPrice)
                        .lineTotal(lineTotal)
                        .build();

                orderReturn.addExchangeItem(exchangeItem);
            }
        }

        // 7. Tính số tiền chênh lệch
        BigDecimal netAmount = totalExchangeAmount.subtract(totalRefundAmount);
        orderReturn.setTotalRefundAmount(totalRefundAmount);
        orderReturn.setTotalExchangeAmount(totalExchangeAmount);
        orderReturn.setNetAmount(netAmount);

        OrderReturn saved = orderReturnRepository.save(orderReturn);
        log.info(">> [ĐỔI - TRẢ HÀNG] Tạo thành công phiếu: {} (Đơn gốc: {}, Loại: {}, Hoàn: {} đ, Đổi: {} đ, Chênh lệch: {} đ)",
                returnCode, orderCode, resolvedType, totalRefundAmount, totalExchangeAmount, netAmount);

        return OrderReturnResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EligibleReturnOrderResponse getEligibleReturnInfo(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }

        Order order = orderRepository.findByOrderCode(orderCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        LocalDate purchaseDate = order.getOrderDate().toLocalDate();
        long daysSincePurchase = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        boolean isEligible = daysSincePurchase <= RETURN_POLICY_DAYS;

        String message = isEligible
                ? "Hóa đơn hợp lệ trong hạn đổi trả (đã mua " + daysSincePurchase + " ngày trước, quy định tối đa " + RETURN_POLICY_DAYS + " ngày)."
                : "Hóa đơn đã quá hạn đổi trả (đã mua " + daysSincePurchase + " ngày trước, quy định tối đa " + RETURN_POLICY_DAYS + " ngày).";

        String custName = "Khách lẻ";
        String custPhone = "—";
        if (order.getCustomer() != null) {
            custName = order.getCustomer().getFullName();
            custPhone = order.getCustomer().getPhone();
        }

        List<EligibleReturnItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem oi : order.getItems()) {
                Product p = oi.getProduct();
                ProductVariant v = oi.getVariant();
                int purchasedQty = oi.getQuantity();
                int alreadyReturned = orderReturnRepository.countReturnedQuantityByOrderItem(oi.getId());
                int remaining = Math.max(0, purchasedQty - alreadyReturned);

                itemResponses.add(EligibleReturnItemResponse.builder()
                        .orderItemId(oi.getId())
                        .productId(p.getId())
                        .variantId(v != null ? v.getId() : null)
                        .variantSku(v != null ? v.getSku() : null)
                        .variantBarcode(v != null ? v.getBarcode() : null)
                        .productCode(p.getCode())
                        .productName(oi.getProductName())
                        .size(v != null && v.getSize() != null ? v.getSize() : p.getSize())
                        .color(v != null && v.getColor() != null ? v.getColor() : p.getColor())
                        .unitPrice(oi.getUnitPrice())
                        .purchasedQuantity(purchasedQty)
                        .alreadyReturnedQuantity(alreadyReturned)
                        .remainingQuantity(remaining)
                        .canReturn(isEligible && remaining > 0)
                        .build());
            }
        }

        return EligibleReturnOrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .orderDate(order.getOrderDate())
                .customerName(custName)
                .customerPhone(custPhone)
                .totalAmount(order.getTotalAmount())
                .daysSincePurchase(daysSincePurchase)
                .eligible(isEligible)
                .message(message)
                .items(itemResponses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getReturnById(Long id) {
        OrderReturn orderReturn = orderReturnRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu đổi / trả hàng với ID: " + id));
        return OrderReturnResponse.fromEntity(orderReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getReturnByCode(String returnCode) {
        OrderReturn orderReturn = orderReturnRepository.findByReturnCodeWithDetails(returnCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu đổi / trả hàng với mã: " + returnCode));
        return OrderReturnResponse.fromEntity(orderReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderReturnResponse> getReturns(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("id").descending());
        Page<OrderReturn> pagedReturns = orderReturnRepository.findAll(pageable);
        return PageResponse.from(pagedReturns.map(OrderReturnResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderReturnResponse> getReturnsByOrderCode(String orderCode) {
        return orderReturnRepository.findByOrderOrderCodeWithDetails(orderCode).stream()
                .map(OrderReturnResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private String generateUniqueReturnCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return "RET-" + timestamp + "-" + suffix;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
