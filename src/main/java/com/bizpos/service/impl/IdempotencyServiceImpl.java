package com.bizpos.service.impl;

import com.bizpos.entity.IdempotencyRecord;
import com.bizpos.entity.IdempotencyStatus;
import com.bizpos.exception.IdempotencyConflictException;
import com.bizpos.repository.IdempotencyRecordRepository;
import com.bizpos.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord startExecution(String idempotencyKey, String endpoint, String requestHash) {
        String trimmedKey = idempotencyKey.trim();

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findByIdempotencyKey(trimmedKey);
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info(">> [IDEMPOTENCY] Cache HIT: Key '{}' đã được xử lý thành công trước đó cho endpoint '{}'",
                        trimmedKey, endpoint);
                return existing;
            }

            if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                // Kiểm tra timeout 2 phút phòng trường hợp server từng crash giữa chừng
                if (existing.getCreatedAt() != null && existing.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                    log.warn(">> [IDEMPOTENCY] Key '{}' bị treo IN_PROGRESS quá 2 phút. Khởi động lại phiên xử lý.", trimmedKey);
                    existing.setStatus(IdempotencyStatus.IN_PROGRESS);
                    existing.setRequestHash(requestHash);
                    return idempotencyRecordRepository.save(existing);
                }

                log.warn(">> [IDEMPOTENCY] Conflict: Key '{}' đang trong quá trình thực thi song song!", trimmedKey);
                throw new IdempotencyConflictException("Yêu cầu với Idempotency-Key '" + trimmedKey + "' đang được xử lý song song. Vui lòng không bấm gửi lại!");
            }

            // Nếu status == FAILED -> Cho phép thử lại
            existing.setStatus(IdempotencyStatus.IN_PROGRESS);
            existing.setRequestHash(requestHash);
            existing.setResponseStatus(null);
            existing.setResponseBody(null);
            return idempotencyRecordRepository.save(existing);
        }

        // Tạo bản ghi mới ở trạng thái IN_PROGRESS
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(trimmedKey)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .build();

        try {
            return idempotencyRecordRepository.save(newRecord);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: luồng song song đã vừa chèn key này
            log.warn(">> [IDEMPOTENCY] Race condition chèn key '{}': {}", trimmedKey, ex.getMessage());
            IdempotencyRecord parallelRecord = idempotencyRecordRepository.findByIdempotencyKey(trimmedKey)
                    .orElseThrow(() -> new IdempotencyConflictException("Xung đột xử lý yêu cầu song song với Idempotency-Key: " + trimmedKey));

            if (parallelRecord.getStatus() == IdempotencyStatus.COMPLETED) {
                return parallelRecord;
            }
            throw new IdempotencyConflictException("Yêu cầu với Idempotency-Key '" + trimmedKey + "' đang được xử lý song song. Vui lòng không bấm gửi lại!");
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
            idempotencyRecordRepository.save(record);
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
}
