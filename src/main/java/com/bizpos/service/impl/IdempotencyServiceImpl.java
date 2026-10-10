package com.bizpos.service.impl;

import com.bizpos.entity.IdempotencyRecord;
import com.bizpos.entity.IdempotencyStatus;
import com.bizpos.exception.IdempotencyConflictException;
import com.bizpos.exception.IdempotencyPayloadMismatchException;
import com.bizpos.repository.IdempotencyRecordRepository;
import com.bizpos.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final int IN_PROGRESS_TTL_SECONDS = 120; // 2 phút timeout cho phiên bị kẹt

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord startExecution(String idempotencyKey, String endpoint, String requestHash) {
        String trimmedKey = idempotencyKey.trim();

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findByIdempotencyKey(trimmedKey);
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // 1. Kiểm tra Payload Mismatch: Cùng key nhưng khác nội dung request -> 422 Unprocessable Entity
            if (existing.getRequestHash() != null && requestHash != null && !requestHash.isBlank()
                    && !existing.getRequestHash().equals(requestHash)) {
                log.warn(">> [IDEMPOTENCY] Payload mismatch cho key '{}'. Đã lưu hash: {}, Hash mới: {}",
                        trimmedKey, existing.getRequestHash(), requestHash);
                throw new IdempotencyPayloadMismatchException(
                        "Idempotency-Key '" + trimmedKey + "' đã được sử dụng trước đó cho một yêu cầu có nội dung khác!");
            }

            // 2. Cache Hit: Đã hoàn thành trước đó
            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info(">> [IDEMPOTENCY] Cache HIT: Key '{}' đã được xử lý thành công trước đó cho endpoint '{}'",
                        trimmedKey, endpoint);
                return existing;
            }

            // 3. Đang xử lý dở dang (IN_PROGRESS)
            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                LocalDateTime refTime = existing.getLockedAt() != null ? existing.getLockedAt() : existing.getCreatedAt();
                // Kiểm tra TTL phòng trường hợp server từng crash/sập giữa chừng
                if (refTime != null && refTime.isBefore(LocalDateTime.now().minusSeconds(IN_PROGRESS_TTL_SECONDS))) {
                    log.warn(">> [IDEMPOTENCY] Key '{}' bị kẹt IN_PROGRESS quá {}s. Tái kích hoạt phiên xử lý mới.",
                            trimmedKey, IN_PROGRESS_TTL_SECONDS);
                    existing.setStatus(IdempotencyStatus.IN_PROGRESS);
                    existing.setLockedAt(LocalDateTime.now());
                    existing.setRequestHash(requestHash);
                    return idempotencyRecordRepository.saveAndFlush(existing);
                }

                log.warn(">> [IDEMPOTENCY] Conflict: Key '{}' đang trong quá trình thực thi song song!", trimmedKey);
                throw new IdempotencyConflictException(
                        "Yêu cầu với Idempotency-Key '" + trimmedKey + "' đang được xử lý song song. Vui lòng không bấm gửi lại!");
            }

            // 4. Trạng thái FAILED (hoặc lỗi trước đó) -> Cho phép thử lại
            existing.setStatus(IdempotencyStatus.IN_PROGRESS);
            existing.setLockedAt(LocalDateTime.now());
            existing.setRequestHash(requestHash);
            existing.setResponseStatus(null);
            existing.setResponseBody(null);
            return idempotencyRecordRepository.saveAndFlush(existing);
        }

        // Tạo bản ghi mới ở trạng thái IN_PROGRESS
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(trimmedKey)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .lockedAt(LocalDateTime.now())
                .build();

        try {
            // Dùng saveAndFlush để ép Hibernate bắn câu lệnh INSERT ngay lập tức, bắt lỗi Unique Constraint
            return idempotencyRecordRepository.saveAndFlush(newRecord);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: luồng song song khác đã vừa chèn key này thành công trước 1 mili-giây
            log.warn(">> [IDEMPOTENCY] Race condition chèn trùng key '{}': {}", trimmedKey, ex.getMessage());
            throw new IdempotencyConflictException(
                    "Yêu cầu với Idempotency-Key '" + trimmedKey + "' đang được xử lý song song. Vui lòng không bấm gửi lại!");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeExecution(String idempotencyKey, int responseStatus, String responseBody) {
        String trimmedKey = idempotencyKey.trim();
        idempotencyRecordRepository.findByIdempotencyKey(trimmedKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            idempotencyRecordRepository.saveAndFlush(record);
            log.info(">> [IDEMPOTENCY] Hoàn thành lưu trữ kết quả cho key '{}' (HTTP {})", trimmedKey, responseStatus);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failExecution(String idempotencyKey) {
        String trimmedKey = idempotencyKey.trim();
        // Xóa bản ghi thất bại để client có thể retry ngay lập tức với cùng key
        idempotencyRecordRepository.deleteByIdempotencyKey(trimmedKey);
        log.info(">> [IDEMPOTENCY] Xóa bản ghi key '{}' sau khi nghiệp vụ thất bại để cho phép thử lại", trimmedKey);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupOldRecords(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, daysToKeep));
        int deleted = idempotencyRecordRepository.deleteByCreatedAtBefore(cutoff);
        log.info(">> [IDEMPOTENCY] Dọn dẹp bản ghi cũ hơn {} ngày: đã xóa {} bản ghi", daysToKeep, deleted);
        return deleted;
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 0 2 * * ?}")
    public void scheduledCleanup() {
        cleanupOldRecords(1);
    }
}
