package com.bizpos.service;

import com.bizpos.entity.IdempotencyRecord;

public interface IdempotencyService {

    /**
     * Bắt đầu phiên thực thi Idempotency.
     * Trả về bản ghi đã hoàn thành (COMPLETED) nếu đã từng chạy,
     * hoặc bản ghi mới (IN_PROGRESS) nếu lần đầu chạy.
     * Ném ngoại lệ 409 Conflict nếu một luồng khác đang xử lý dở dang (IN_PROGRESS).
     */
    IdempotencyRecord startExecution(String idempotencyKey, String endpoint, String requestHash);

    /**
     * Đánh dấu phiên xử lý đã hoàn thành (COMPLETED) và lưu lại kết quả JSON trả về.
     */
    void completeExecution(String idempotencyKey, int responseStatus, String responseBody);

    /**
     * Đánh dấu hoặc xóa phiên xử lý khi nghiệp vụ thất bại, cho phép client retry.
     */
    void failExecution(String idempotencyKey);

    /**
     * Dọn dẹp các bản ghi idempotency cũ hơn số ngày chỉ định.
     */
    int cleanupOldRecords(int daysToKeep);
}
