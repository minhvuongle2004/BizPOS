package com.bizpos.controller;

import com.bizpos.dto.EligibleReturnOrderResponse;
import com.bizpos.dto.OrderReturnRequest;
import com.bizpos.dto.OrderReturnResponse;
import com.bizpos.dto.PageResponse;
import com.bizpos.service.OrderReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class OrderReturnController {

    private final OrderReturnService orderReturnService;

    /**
     * 1. Xử lý tạo phiếu đổi / trả hàng (Áp dụng Idempotency-Key chống tạo phiếu lặp)
     * POST /api/returns
     */
    @PostMapping
    @com.bizpos.aspect.Idempotent
    public ResponseEntity<OrderReturnResponse> processReturn(@Valid @RequestBody OrderReturnRequest request) {
        OrderReturnResponse response = orderReturnService.processReturn(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Tra cứu điều kiện đổi trả của một đơn hàng (Kiểm tra hạn 7 ngày & số lượng còn được đổi)
     * GET /api/returns/eligible/{orderCode}
     */
    @GetMapping("/eligible/{orderCode}")
    public ResponseEntity<EligibleReturnOrderResponse> getEligibleReturnInfo(@PathVariable String orderCode) {
        EligibleReturnOrderResponse response = orderReturnService.getEligibleReturnInfo(orderCode);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Lấy chi tiết phiếu đổi / trả theo ID
     * GET /api/returns/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderReturnResponse> getReturnById(@PathVariable Long id) {
        OrderReturnResponse response = orderReturnService.getReturnById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. Lấy chi tiết phiếu đổi / trả theo mã phiếu
     * GET /api/returns/code/{returnCode}
     */
    @GetMapping("/code/{returnCode}")
    public ResponseEntity<OrderReturnResponse> getReturnByCode(@PathVariable String returnCode) {
        OrderReturnResponse response = orderReturnService.getReturnByCode(returnCode);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. Lấy danh sách phiếu đổi trả có phân trang
     * GET /api/returns?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<PageResponse<OrderReturnResponse>> getReturns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<OrderReturnResponse> response = orderReturnService.getReturns(page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Lấy lịch sử tất cả các phiếu đổi trả của 1 đơn hàng gốc
     * GET /api/returns/by-order/{orderCode}
     */
    @GetMapping("/by-order/{orderCode}")
    public ResponseEntity<List<OrderReturnResponse>> getReturnsByOrderCode(@PathVariable String orderCode) {
        List<OrderReturnResponse> response = orderReturnService.getReturnsByOrderCode(orderCode);
        return ResponseEntity.ok(response);
    }
}
