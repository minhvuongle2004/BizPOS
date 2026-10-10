package com.bizpos.service.impl;

import com.bizpos.dto.StockMovementResponse;
import com.bizpos.entity.MovementType;
import com.bizpos.entity.Product;
import com.bizpos.entity.StockMovement;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.ProductRepository;
import com.bizpos.repository.StockMovementRepository;
import com.bizpos.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public StockMovement recordMovement(
            Product product,
            MovementType type,
            int quantity,
            int previousStock,
            int currentStock,
            String referenceCode,
            String reason,
            String createdBy) {
        return recordMovement(product, null, type, quantity, previousStock, currentStock, referenceCode, reason, createdBy);
    }

    @Override
    @Transactional
    public StockMovement recordMovement(
            Product product,
            com.bizpos.entity.ProductVariant variant,
            MovementType type,
            int quantity,
            int previousStock,
            int currentStock,
            String referenceCode,
            String reason,
            String createdBy) {

        if (product == null && variant != null) {
            product = variant.getProduct();
        }

        if (product == null) {
            throw new IllegalArgumentException("Sản phẩm không được để trống khi ghi nhật ký kho!");
        }
        if (type == null) {
            throw new IllegalArgumentException("Loại biến động kho (MovementType) không được để trống!");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng biến động kho phải lớn hơn 0!");
        }

        String user = (createdBy != null && !createdBy.trim().isEmpty()) ? createdBy.trim() : "SYSTEM";

        StockMovement movement = StockMovement.builder()
                .product(product)
                .variant(variant)
                .type(type)
                .quantity(quantity)
                .previousStock(previousStock)
                .currentStock(currentStock)
                .referenceCode(referenceCode)
                .reason(reason)
                .createdBy(user)
                .build();

        StockMovement saved = stockMovementRepository.save(movement);
        log.info(">> [THẺ KHO] Sản phẩm: {} (Mã: {}), Loại: {}, Biến động: {}, Tồn trước: {}, Tồn sau: {}, Ref: {}, Người tạo: {}",
                product.getName(), product.getCode(), type, quantity, previousStock, currentStock, referenceCode, user);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getMovementsByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }

        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(StockMovementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovementsByProductId(Long productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }

        return stockMovementRepository.findByProductId(productId, pageable)
                .map(StockMovementResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getAllMovements(Pageable pageable) {
        return stockMovementRepository.findAllWithProduct(pageable)
                .map(StockMovementResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> filterMovements(
            Long productId,
            MovementType type,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        return stockMovementRepository.filterMovements(productId, type, from, to, pageable)
                .map(StockMovementResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyProductStockReconciliation(Long productId) {
        com.bizpos.dto.StockReconciliationReport report = reconcileProductStock(productId);
        return report.isReconciled();
    }

    @Override
    @Transactional(readOnly = true)
    public com.bizpos.dto.StockReconciliationReport reconcileProductStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtAsc(productId);
        int currentStockOnProduct = product.getStockQuantity() != null ? product.getStockQuantity() : 0;

        if (movements.isEmpty()) {
            return com.bizpos.dto.StockReconciliationReport.builder()
                    .productId(product.getId())
                    .productCode(product.getCode())
                    .productName(product.getName())
                    .initialStock(currentStockOnProduct)
                    .totalDelta(0)
                    .calculatedStock(currentStockOnProduct)
                    .currentStock(currentStockOnProduct)
                    .movementCount(0)
                    .isReconciled(true)
                    .message("Chưa có biến động kho nào phát sinh; số liệu toàn vẹn.")
                    .build();
        }

        // 1. Kiểm tra tính liên tục của chuỗi biến động (Chain continuity) & Tổng Delta có dấu (Sigma Delta)
        int initialStock = movements.get(0).getPreviousStock();
        int sumDelta = 0;

        for (int i = 0; i < movements.size(); i++) {
            StockMovement current = movements.get(i);
            int signedDelta = current.getSignedDelta();
            sumDelta += signedDelta;

            // Kiểm tra đứt gãy chuỗi: previousStock của bản ghi i phải khớp currentStock của bản ghi i-1
            if (i > 0) {
                StockMovement previous = movements.get(i - 1);
                if (!current.getPreviousStock().equals(previous.getCurrentStock())) {
                    log.warn(">> [RECONCILIATION FAILED] Đứt gãy chuỗi thẻ kho tại bản ghi #{}: previousStock ({}) khác currentStock bản ghi trước ({})",
                            current.getId(), current.getPreviousStock(), previous.getCurrentStock());
                    return com.bizpos.dto.StockReconciliationReport.builder()
                            .productId(product.getId())
                            .productCode(product.getCode())
                            .productName(product.getName())
                            .initialStock(initialStock)
                            .totalDelta(sumDelta)
                            .calculatedStock(initialStock + sumDelta)
                            .currentStock(currentStockOnProduct)
                            .movementCount(movements.size())
                            .isReconciled(false)
                            .message("Đứt gãy chuỗi thẻ kho tại bản ghi #" + current.getId())
                            .build();
                }
            }
        }

        // 2. Tính toán: Tồn kho kỳ vọng = Tồn ban đầu + Tổng Delta (Sigma Delta: Tồn hiện tại = Tồn ban đầu + Σ delta)
        int expectedFinalStock = initialStock + sumDelta;
        StockMovement latestMovement = movements.get(movements.size() - 1);

        boolean isReconciled = latestMovement.getCurrentStock().equals(currentStockOnProduct)
                && expectedFinalStock == currentStockOnProduct;

        String message = isReconciled
                ? "Sổ cái khớp 100% với tồn kho thực tế (Tồn ban đầu " + initialStock + " + Σ Delta " + sumDelta + " = " + expectedFinalStock + ")"
                : "Lệch tồn kho: Tính toán từ sổ cái (" + expectedFinalStock + ") khác tồn thực tế (" + currentStockOnProduct + ")";

        return com.bizpos.dto.StockReconciliationReport.builder()
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .initialStock(initialStock)
                .totalDelta(sumDelta)
                .calculatedStock(expectedFinalStock)
                .currentStock(currentStockOnProduct)
                .movementCount(movements.size())
                .isReconciled(isReconciled)
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.bizpos.dto.StockReconciliationReport> reconcileAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> reconcileProductStock(p.getId()))
                .collect(Collectors.toList());
    }
}
