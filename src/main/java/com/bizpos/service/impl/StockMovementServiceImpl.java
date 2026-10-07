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
}
