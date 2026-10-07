package com.bizpos.service;

import com.bizpos.dto.AuditLogResponse;
import com.bizpos.entity.AuditLog;
import com.bizpos.repository.AuditLogRepository;
import com.bizpos.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho AuditLogService (Nhật ký kiểm toán hệ thống)")
public class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = AuditLog.builder()
                .id(1L)
                .entityName("Product")
                .entityId("10")
                .action("UPDATE_PRICE")
                .actionDescription("Thay đổi giá bán sản phẩm")
                .oldValue("Giá: 50,000 đ")
                .newValue("Giá: 30,000 đ")
                .details("Đổi giá bán sản phẩm từ 50,000 đ thành 30,000 đ")
                .performedBy("admin")
                .ipAddress("127.0.0.1")
                .build();
        sampleLog.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("1. Ghi nhận nhật ký kiểm toán (recordLog) thành công")
    void testRecordLog_Success() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(sampleLog);

        AuditLog saved = auditLogService.recordLog(
                "Product",
                "10",
                "UPDATE_PRICE",
                "Thay đổi giá bán sản phẩm",
                "Giá: 50,000 đ",
                "Giá: 30,000 đ",
                "Đổi giá bán sản phẩm từ 50,000 đ thành 30,000 đ",
                "admin",
                "127.0.0.1"
        );

        assertNotNull(saved);
        assertEquals("Product", saved.getEntityName());
        assertEquals("UPDATE_PRICE", saved.getAction());
        assertEquals("admin", saved.getPerformedBy());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog captured = captor.getValue();
        assertEquals("Product", captured.getEntityName());
        assertEquals("10", captured.getEntityId());
        assertEquals("admin", captured.getPerformedBy());
    }

    @Test
    @DisplayName("2. Fallback sang SYSTEM và 127.0.0.1 khi username hoặc IP để trống")
    void testRecordLog_FallbackUserAndIp() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditLogService.recordLog(
                "Order",
                "ORD-123",
                "DELETE_ORDER",
                "Hủy đơn hàng",
                "Tổng tiền: 100,000 đ",
                null,
                "Hủy đơn",
                null,
                ""
        );

        assertEquals("SYSTEM", saved.getPerformedBy());
        assertEquals("127.0.0.1", saved.getIpAddress());
    }

    @Test
    @DisplayName("3. Lấy toàn bộ nhật ký phân trang (getAllLogs)")
    void testGetAllLogs_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(sampleLog)));

        Page<AuditLogResponse> result = auditLogService.getAllLogs(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        AuditLogResponse dto = result.getContent().get(0);
        assertEquals("UPDATE_PRICE", dto.getAction());
        assertEquals("admin", dto.getPerformedBy());
    }

    @Test
    @DisplayName("4. Lấy lịch sử can thiệp theo Entity (getLogsByEntity)")
    void testGetLogsByEntity_Success() {
        when(auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Product", "10"))
                .thenReturn(List.of(sampleLog));

        List<AuditLogResponse> list = auditLogService.getLogsByEntity("Product", "10");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Product", list.get(0).getEntityName());
        assertEquals("10", list.get(0).getEntityId());
    }
}
