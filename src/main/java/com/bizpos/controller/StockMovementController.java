package com.bizpos.controller;

import com.bizpos.dto.StockMovementResponse;
import com.bizpos.entity.MovementType;
import com.bizpos.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService stockMovementService;

    /**
     * 1. Lấy toàn bộ lịch sử biến động kho của 1 sản phẩm cụ thể
     * GET /api/stock-movements/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockMovementResponse>> getMovementsByProductId(@PathVariable Long productId) {
        List<StockMovementResponse> movements = stockMovementService.getMovementsByProductId(productId);
        return ResponseEntity.ok(movements);
    }

    /**
     * 2. Lấy lịch sử biến động kho của 1 sản phẩm có phân trang
     * GET /api/stock-movements/product/{productId}/paged?page=0&size=20
     */
    @GetMapping("/product/{productId}/paged")
    public ResponseEntity<Page<StockMovementResponse>> getPagedMovementsByProductId(
            @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovementResponse> movements = stockMovementService.getMovementsByProductId(productId, pageable);
        return ResponseEntity.ok(movements);
    }

    /**
     * 3. Sổ nhật ký kho tổng thể toàn hệ thống (có bộ lọc linh hoạt)
     * GET /api/stock-movements?productId=...&type=SALE&from=...&to=...&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<StockMovementResponse>> getAllMovements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<StockMovementResponse> movements = stockMovementService.filterMovements(productId, type, from, to, pageable);
        return ResponseEntity.ok(movements);
    }
}
