package com.bizpos.service;

import com.bizpos.dto.EligibleReturnOrderResponse;
import com.bizpos.dto.OrderReturnRequest;
import com.bizpos.dto.OrderReturnResponse;
import com.bizpos.dto.PageResponse;

import java.util.List;

public interface OrderReturnService {

    /**
     * Xử lý tạo phiếu đổi / trả hàng thời trang (Return & Exchange)
     */
    OrderReturnResponse processReturn(OrderReturnRequest request);

    /**
     * Tra cứu thông tin điều kiện đổi trả của đơn hàng (số lượng còn được đổi, hạn đổi trả 7 ngày)
     */
    EligibleReturnOrderResponse getEligibleReturnInfo(String orderCode);

    /**
     * Lấy chi tiết phiếu đổi / trả theo ID
     */
    OrderReturnResponse getReturnById(Long id);

    /**
     * Lấy chi tiết phiếu đổi / trả theo mã phiếu
     */
    OrderReturnResponse getReturnByCode(String returnCode);

    /**
     * Lấy danh sách phiếu đổi / trả có phân trang
     */
    PageResponse<OrderReturnResponse> getReturns(int page, int size);

    /**
     * Lấy danh sách phiếu đổi / trả theo mã đơn hàng gốc
     */
    List<OrderReturnResponse> getReturnsByOrderCode(String orderCode);
}
