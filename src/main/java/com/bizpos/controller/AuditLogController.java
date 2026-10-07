package com.bizpos.controller;

import com.bizpos.dto.AuditLogResponse;
import com.bizpos.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Lấy danh sách nhật ký kiểm toán (Audit Logs) có phân trang và lọc đa tiêu chí
     * GET /api/audit-logs?page=0&size=20&entityName=Product&action=UPDATE_PRICE
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("createdAt").descending());

        if (entityName != null || action != null || from != null || to != null) {
            return ResponseEntity.ok(auditLogService.filterLogs(entityName, action, from, to, pageable));
        }

        return ResponseEntity.ok(auditLogService.getAllLogs(pageable));
    }

    /**
     * Lấy lịch sử can thiệp của một thực thể cụ thể (ví dụ: Product id=10, Order mã ORD-xxx)
     * GET /api/audit-logs/entity?entityName=Product&entityId=10
     */
    @GetMapping("/entity")
    public ResponseEntity<List<AuditLogResponse>> getLogsByEntity(
            @RequestParam String entityName,
            @RequestParam String entityId) {

        return ResponseEntity.ok(auditLogService.getLogsByEntity(entityName, entityId));
    }
}
