package com.bizpos.aspect;

import com.bizpos.entity.Order;
import com.bizpos.entity.Product;
import com.bizpos.repository.OrderRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

@Slf4j
@Aspect
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Around("@annotation(auditable)")
    public Object auditOperation(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String entity = auditable.entity();
        String action = auditable.action();

        if ("Product".equalsIgnoreCase(entity)) {
            return auditProductOperation(joinPoint, action);
        } else if ("Order".equalsIgnoreCase(entity)) {
            return auditOrderOperation(joinPoint, action);
        } else if ("OrderReturn".equalsIgnoreCase(entity)) {
            return auditOrderReturnOperation(joinPoint, action);
        }

        // Mặc định cho các đối tượng khác nếu có
        return joinPoint.proceed();
    }

    private Object auditProductOperation(ProceedingJoinPoint joinPoint, String action) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Long id)) {
            return joinPoint.proceed();
        }

        if ("UPDATE_PRODUCT".equalsIgnoreCase(action)) {
            Product oldProduct = productRepository.findById(id).orElse(null);
            BigDecimal oldPrice = oldProduct != null ? oldProduct.getPrice() : BigDecimal.ZERO;
            String oldName = oldProduct != null ? oldProduct.getName() : "";
            String oldCode = oldProduct != null ? oldProduct.getCode() : "";
            Integer oldStock = oldProduct != null ? oldProduct.getStockQuantity() : 0;

            try {
                Object result = joinPoint.proceed();

                if (result instanceof Product updated) {
                    BigDecimal newPrice = updated.getPrice();
                    boolean isPriceChanged = oldPrice != null && newPrice != null && oldPrice.compareTo(newPrice) != 0;

                    String finalAction = isPriceChanged ? "UPDATE_PRICE" : "UPDATE_PRODUCT";
                    String actionDesc = isPriceChanged ? "Thay đổi giá bán sản phẩm" : "Cập nhật thông tin sản phẩm";

                    String oldValueStr = String.format("Mã: %s | Tên: %s | Giá: %,.0f đ | Tồn: %d", oldCode, oldName, oldPrice, oldStock);
                    String newValueStr = String.format("Mã: %s | Tên: %s | Giá: %,.0f đ | Tồn: %d", updated.getCode(), updated.getName(), newPrice, updated.getStockQuantity());

                    String details;
                    if (isPriceChanged) {
                        details = String.format("Sửa giá sản phẩm '%s' (Mã: %s) từ %,.0f đ -> %,.0f đ",
                                updated.getName(), updated.getCode(), oldPrice, newPrice);
                    } else {
                        details = String.format("Cập nhật thông tin sản phẩm '%s' (Mã: %s)", updated.getName(), updated.getCode());
                    }

                    auditLogService.recordSuccessLog("Product", String.valueOf(id), finalAction, actionDesc,
                            oldValueStr, newValueStr, details, getCurrentUsername(), getClientIp());
                }

                return result;
            } catch (Throwable ex) {
                auditLogService.recordFailureLog("Product", String.valueOf(id), "UPDATE_PRODUCT_FAILED", "Cập nhật sản phẩm thất bại",
                        String.format("Mã: %s | Tên: %s", oldCode, oldName), null,
                        "Lỗi khi cập nhật sản phẩm: " + ex.getMessage(), getCurrentUsername(), getClientIp());
                throw ex;
            }

        } else if ("DELETE_PRODUCT".equalsIgnoreCase(action)) {
            Product oldProduct = productRepository.findById(id).orElse(null);
            String oldInfo = oldProduct != null
                    ? String.format("Mã: %s | Tên: %s | Giá: %,.0f đ | Tồn: %d",
                    oldProduct.getCode(), oldProduct.getName(), oldProduct.getPrice(), oldProduct.getStockQuantity())
                    : "ID: " + id;
            String details = oldProduct != null
                    ? String.format("Xóa vĩnh viễn sản phẩm '%s' (Mã: %s)", oldProduct.getName(), oldProduct.getCode())
                    : "Xóa sản phẩm ID: " + id;

            try {
                Object result = joinPoint.proceed();

                auditLogService.recordSuccessLog("Product", String.valueOf(id), "DELETE_PRODUCT", "Xóa sản phẩm",
                        oldInfo, null, details, getCurrentUsername(), getClientIp());

                return result;
            } catch (Throwable ex) {
                auditLogService.recordFailureLog("Product", String.valueOf(id), "DELETE_PRODUCT_FAILED", "Xóa sản phẩm thất bại",
                        oldInfo, null, "Lỗi khi xóa sản phẩm: " + ex.getMessage(), getCurrentUsername(), getClientIp());
                throw ex;
            }

        } else if ("ADJUST_STOCK".equalsIgnoreCase(action)) {
            Product oldProduct = productRepository.findById(id).orElse(null);
            int oldStock = (oldProduct != null && oldProduct.getStockQuantity() != null) ? oldProduct.getStockQuantity() : 0;
            Integer requestedStock = (args.length > 1 && args[1] instanceof Integer q) ? q : null;

            try {
                Object result = joinPoint.proceed();

                int newStock = (result instanceof Product updated && updated.getStockQuantity() != null)
                        ? updated.getStockQuantity()
                        : (requestedStock != null ? requestedStock : 0);
                int delta = newStock - oldStock;
                String pName = oldProduct != null ? oldProduct.getName() : "ID " + id;
                String pCode = oldProduct != null ? oldProduct.getCode() : "";

                String oldValueStr = String.format("Mã: %s | Tên: %s | Tồn cũ: %d", pCode, pName, oldStock);
                String newValueStr = String.format("Mã: %s | Tên: %s | Tồn mới: %d", pCode, pName, newStock);
                String details = String.format("Điều chỉnh tồn kho sản phẩm '%s' (Mã: %s) từ %d -> %d cái (Chênh lệch: %+d)",
                        pName, pCode, oldStock, newStock, delta);

                auditLogService.recordSuccessLog("Product", String.valueOf(id), "ADJUST_STOCK", "Điều chỉnh tồn kho thủ công",
                        oldValueStr, newValueStr, details, getCurrentUsername(), getClientIp());

                return result;
            } catch (Throwable ex) {
                String pName = oldProduct != null ? oldProduct.getName() : "ID " + id;
                String details = String.format("Thất bại khi điều chỉnh tồn kho sản phẩm '%s' (Mục tiêu: %s cái): %s",
                        pName, requestedStock, ex.getMessage());

                auditLogService.recordFailureLog("Product", String.valueOf(id), "ADJUST_STOCK_FAILED", "Điều chỉnh tồn kho thất bại",
                        "Tồn: " + oldStock, "Yêu cầu: " + requestedStock, details, getCurrentUsername(), getClientIp());
                throw ex;
            }
        }

        return joinPoint.proceed();
    }

    private Object auditOrderOperation(ProceedingJoinPoint joinPoint, String action) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Long id)) {
            return joinPoint.proceed();
        }

        if ("UPDATE_ORDER".equalsIgnoreCase(action)) {
            Order oldOrder = orderRepository.findByIdWithDetails(id).orElseGet(() -> orderRepository.findById(id).orElse(null));
            BigDecimal oldTotal = oldOrder != null ? oldOrder.getTotalAmount() : BigDecimal.ZERO;
            String orderCode = oldOrder != null ? oldOrder.getOrderCode() : String.valueOf(id);
            int oldItemCount = 0;
            try {
                if (oldOrder != null && oldOrder.getItems() != null) {
                    oldItemCount = oldOrder.getItems().size();
                }
            } catch (Exception ignored) {}

            try {
                Object result = joinPoint.proceed();

                if (result instanceof Order updatedOrder) {
                    BigDecimal newTotal = updatedOrder.getTotalAmount();
                    int newItemCount = 0;
                    try {
                        if (updatedOrder.getItems() != null) {
                            newItemCount = updatedOrder.getItems().size();
                        }
                    } catch (Exception ignored) {}

                    String oldValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", orderCode, oldTotal, oldItemCount);
                    String newValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", updatedOrder.getOrderCode(), newTotal, newItemCount);
                    String details = String.format("Chỉnh sửa hóa đơn %s: Tổng tiền thay đổi từ %,.0f đ -> %,.0f đ (%d món -> %d món)",
                            updatedOrder.getOrderCode(), oldTotal, newTotal, oldItemCount, newItemCount);

                    auditLogService.recordSuccessLog("Order", updatedOrder.getOrderCode(), "UPDATE_ORDER", "Chỉnh sửa hóa đơn",
                            oldValueStr, newValueStr, details, getCurrentUsername(), getClientIp());
                }

                return result;
            } catch (Throwable ex) {
                auditLogService.recordFailureLog("Order", orderCode, "UPDATE_ORDER_FAILED", "Chỉnh sửa hóa đơn thất bại",
                        String.format("Mã đơn: %s | Tổng tiền: %,.0f đ", orderCode, oldTotal), null,
                        "Lỗi khi sửa hóa đơn: " + ex.getMessage(), getCurrentUsername(), getClientIp());
                throw ex;
            }

        } else if ("DELETE_ORDER".equalsIgnoreCase(action)) {
            Order oldOrder = orderRepository.findByIdWithDetails(id).orElseGet(() -> orderRepository.findById(id).orElse(null));
            String orderCode = oldOrder != null ? oldOrder.getOrderCode() : String.valueOf(id);
            BigDecimal total = oldOrder != null ? oldOrder.getTotalAmount() : BigDecimal.ZERO;
            int itemCount = 0;
            try {
                if (oldOrder != null && oldOrder.getItems() != null) {
                    itemCount = oldOrder.getItems().size();
                }
            } catch (Exception ignored) {}

            String oldValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", orderCode, total, itemCount);
            String details = String.format("Hủy / Xóa hóa đơn %s (Tổng tiền: %,.0f đ, %d món)", orderCode, total, itemCount);

            try {
                Object result = joinPoint.proceed();

                auditLogService.recordSuccessLog("Order", orderCode, "DELETE_ORDER", "Hủy hóa đơn",
                        oldValueStr, null, details, getCurrentUsername(), getClientIp());

                return result;
            } catch (Throwable ex) {
                auditLogService.recordFailureLog("Order", orderCode, "DELETE_ORDER_FAILED", "Hủy hóa đơn thất bại",
                        oldValueStr, null, "Lỗi khi hủy hóa đơn: " + ex.getMessage(), getCurrentUsername(), getClientIp());
                throw ex;
            }
        }

        return joinPoint.proceed();
    }

    private Object auditOrderReturnOperation(ProceedingJoinPoint joinPoint, String action) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            if (result instanceof com.bizpos.dto.OrderReturnResponse ret) {
                auditLogService.recordSuccessLog(
                        "OrderReturn",
                        ret.getReturnCode(),
                        "PROCESS_RETURN",
                        "Xử lý đổi - trả hàng",
                        null,
                        "Mã phiếu: " + ret.getReturnCode(),
                        String.format("Xử lý phiếu đổi - trả %s (Đơn: %s, Loại: %s, Hoàn: %,.0f đ, Đổi: %,.0f đ)",
                                ret.getReturnCode(), ret.getOrderCode(), ret.getReturnType(), ret.getTotalRefundAmount(), ret.getTotalExchangeAmount()),
                        getCurrentUsername(),
                        getClientIp()
                );
            }
            return result;
        } catch (Throwable ex) {
            auditLogService.recordFailureLog(
                    "OrderReturn",
                    "UNKNOWN",
                    "PROCESS_RETURN_FAILED",
                    "Đổi - trả hàng thất bại",
                    null,
                    null,
                    "Thất bại khi xử lý phiếu đổi - trả: " + ex.getMessage(),
                    getCurrentUsername(),
                    getClientIp()
            );
            throw ex;
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xf = req.getHeader("X-Forwarded-For");
                if (xf != null && !xf.isEmpty()) {
                    return xf.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
