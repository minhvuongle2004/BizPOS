package com.bizpos.service;

import com.bizpos.dto.AuditLogResponse;
import com.bizpos.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

    /**
     * Ghi Audit Log cho đường THÀNH CÔNG (Success Path) - Chạy CÙNG TRANSACTION với nghiệp vụ (Propagation.REQUIRED).
     * Rollback cùng nhau nếu nghiệp vụ lỗi, commit cùng nhau để chống "log ma" (phantom log).
     */
    AuditLog recordSuccessLog(
            String entityName,
            String entityId,
            String action,
            String actionDescription,
            String oldValue,
            String newValue,
            String details,
            String performedBy,
            String ipAddress);

    /**
     * Ghi Audit Log cho đường THẤT BẠI (Failure Path / LOGIN_FAILED) - Chạy TRANSACTION ĐỘC LẬP (Propagation.REQUIRES_NEW).
     * Đảm bảo dấu vết sự cố/gian lận được bảo toàn vĩnh viễn kể cả khi nghiệp vụ chính bị rollback.
     */
    AuditLog recordFailureLog(
            String entityName,
            String entityId,
            String action,
            String actionDescription,
            String oldValue,
            String newValue,
            String details,
            String performedBy,
            String ipAddress);

    /**
     * Phương thức mặc định (Tương thích ngược) - Sử dụng cùng Transaction với nghiệp vụ (Propagation.REQUIRED)
     */
    AuditLog recordLog(
            String entityName,
            String entityId,
            String action,
            String actionDescription,
            String oldValue,
            String newValue,
            String details,
            String performedBy,
            String ipAddress);

    Page<AuditLogResponse> getAllLogs(Pageable pageable);

    List<AuditLogResponse> getLogsByEntity(String entityName, String entityId);

    Page<AuditLogResponse> filterLogs(
            String entityName,
            String action,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}
