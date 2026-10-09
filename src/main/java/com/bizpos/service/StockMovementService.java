package com.bizpos.service;

import com.bizpos.dto.StockMovementResponse;
import com.bizpos.entity.MovementType;
import com.bizpos.entity.Product;
import com.bizpos.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementService {

    /**
     * Ghi nhận một biến động kho vào sổ nhật ký (Stock Movement Ledger)
     */
    StockMovement recordMovement(
            Product product,
            MovementType type,
            int quantity,
            int previousStock,
            int currentStock,
            String referenceCode,
            String reason,
            String createdBy);

    /**
     * Ghi nhận một biến động kho chi tiết tới từng biến thể (ProductVariant)
     */
    StockMovement recordMovement(
            Product product,
            com.bizpos.entity.ProductVariant variant,
            MovementType type,
            int quantity,
            int previousStock,
            int currentStock,
            String referenceCode,
            String reason,
            String createdBy);

    /**
     * Lấy toàn bộ lịch sử biến động kho của 1 sản phẩm
     */
    List<StockMovementResponse> getMovementsByProductId(Long productId);

    /**
     * Lấy lịch sử biến động kho của 1 sản phẩm có phân trang
     */
    Page<StockMovementResponse> getMovementsByProductId(Long productId, Pageable pageable);

    /**
     * Lấy toàn bộ nhật ký kho của hệ thống có phân trang
     */
    Page<StockMovementResponse> getAllMovements(Pageable pageable);

    /**
     * Lọc nhật ký kho theo sản phẩm, loại biến động, thời gian
     */
    Page<StockMovementResponse> filterMovements(
            Long productId,
            MovementType type,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}
