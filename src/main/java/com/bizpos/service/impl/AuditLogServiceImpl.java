package com.bizpos.service.impl;

import com.bizpos.dto.AuditLogResponse;
import com.bizpos.entity.AuditLog;
import com.bizpos.repository.AuditLogRepository;
import com.bizpos.service.AuditLogService;
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
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public AuditLog recordLog(
            String entityName,
            String entityId,
            String action,
            String actionDescription,
            String oldValue,
            String newValue,
            String details,
            String performedBy,
            String ipAddress) {

        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : "SYSTEM";
        String ip = (ipAddress != null && !ipAddress.trim().isEmpty()) ? ipAddress.trim() : "127.0.0.1";

        AuditLog auditLog = AuditLog.builder()
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .actionDescription(actionDescription)
                .oldValue(oldValue)
                .newValue(newValue)
                .details(details)
                .performedBy(user)
                .ipAddress(ip)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info(">> [AUDIT LOG] Entity: {} (ID: {}), Action: {}, By: {}, Details: {}",
                entityName, entityId, action, user, details);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AuditLogResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogsByEntity(String entityName, String entityId) {
        return auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId).stream()
                .map(AuditLogResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> filterLogs(
            String entityName,
            String action,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        return auditLogRepository.filterLogs(entityName, action, from, to, pageable)
                .map(AuditLogResponse::fromEntity);
    }
}
