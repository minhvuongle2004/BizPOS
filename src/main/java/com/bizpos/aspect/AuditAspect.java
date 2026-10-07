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

                auditLogService.recordLog("Product", String.valueOf(id), finalAction, actionDesc,
                        oldValueStr, newValueStr, details, getCurrentUsername(), getClientIp());
            }

            return result;

        } else if ("DELETE_PRODUCT".equalsIgnoreCase(action)) {
            Product oldProduct = productRepository.findById(id).orElse(null);
            String oldInfo = oldProduct != null
                    ? String.format("Mã: %s | Tên: %s | Giá: %,.0f đ | Tồn: %d",
                    oldProduct.getCode(), oldProduct.getName(), oldProduct.getPrice(), oldProduct.getStockQuantity())
                    : "ID: " + id;
            String details = oldProduct != null
                    ? String.format("Xóa vĩnh viễn sản phẩm '%s' (Mã: %s)", oldProduct.getName(), oldProduct.getCode())
                    : "Xóa sản phẩm ID: " + id;

            Object result = joinPoint.proceed();

            auditLogService.recordLog("Product", String.valueOf(id), "DELETE_PRODUCT", "Xóa sản phẩm",
                    oldInfo, null, details, getCurrentUsername(), getClientIp());

            return result;
        }

        return joinPoint.proceed();
    }

    private Object auditOrderOperation(ProceedingJoinPoint joinPoint, String action) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Long id)) {
            return joinPoint.proceed();
        }

        if ("UPDATE_ORDER".equalsIgnoreCase(action)) {
            Order oldOrder = orderRepository.findById(id).orElse(null);
            BigDecimal oldTotal = oldOrder != null ? oldOrder.getTotalAmount() : BigDecimal.ZERO;
            String orderCode = oldOrder != null ? oldOrder.getOrderCode() : String.valueOf(id);
            int oldItemCount = (oldOrder != null && oldOrder.getItems() != null) ? oldOrder.getItems().size() : 0;

            Object result = joinPoint.proceed();

            if (result instanceof Order updatedOrder) {
                BigDecimal newTotal = updatedOrder.getTotalAmount();
                int newItemCount = updatedOrder.getItems() != null ? updatedOrder.getItems().size() : 0;

                String oldValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", orderCode, oldTotal, oldItemCount);
                String newValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", updatedOrder.getOrderCode(), newTotal, newItemCount);
                String details = String.format("Chỉnh sửa hóa đơn %s: Tổng tiền thay đổi từ %,.0f đ -> %,.0f đ (%d món -> %d món)",
                        updatedOrder.getOrderCode(), oldTotal, newTotal, oldItemCount, newItemCount);

                auditLogService.recordLog("Order", updatedOrder.getOrderCode(), "UPDATE_ORDER", "Chỉnh sửa hóa đơn",
                        oldValueStr, newValueStr, details, getCurrentUsername(), getClientIp());
            }

            return result;

        } else if ("DELETE_ORDER".equalsIgnoreCase(action)) {
            Order oldOrder = orderRepository.findById(id).orElse(null);
            String orderCode = oldOrder != null ? oldOrder.getOrderCode() : String.valueOf(id);
            BigDecimal total = oldOrder != null ? oldOrder.getTotalAmount() : BigDecimal.ZERO;
            int itemCount = (oldOrder != null && oldOrder.getItems() != null) ? oldOrder.getItems().size() : 0;

            String oldValueStr = String.format("Mã đơn: %s | Tổng tiền: %,.0f đ (%d món)", orderCode, total, itemCount);
            String details = String.format("Hủy / Xóa hóa đơn %s (Tổng tiền: %,.0f đ, %d món)", orderCode, total, itemCount);

            Object result = joinPoint.proceed();

            auditLogService.recordLog("Order", orderCode, "DELETE_ORDER", "Hủy hóa đơn",
                    oldValueStr, null, details, getCurrentUsername(), getClientIp());

            return result;
        }

        return joinPoint.proceed();
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
