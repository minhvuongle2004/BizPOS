package com.bizpos.service;

import com.bizpos.dto.AuditLogResponse;
import com.bizpos.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

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
