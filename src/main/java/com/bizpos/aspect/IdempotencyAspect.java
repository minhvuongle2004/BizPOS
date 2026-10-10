package com.bizpos.aspect;

import com.bizpos.entity.IdempotencyRecord;
import com.bizpos.entity.IdempotencyStatus;
import com.bizpos.service.IdempotencyService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = getCurrentHttpRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String key = request.getHeader(idempotent.headerName());
        if (key == null || key.trim().isEmpty()) {
            key = request.getHeader("X-Idempotency-Key");
        }

        // Nếu client không truyền header Idempotency-Key
        if (key == null || key.trim().isEmpty()) {
            if (idempotent.required()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Header '" + idempotent.headerName() + "' là bắt buộc đối với thao tác này!");
            }
            return joinPoint.proceed();
        }

        String trimmedKey = key.trim();
        String endpoint = request.getRequestURI();
        String requestHash = computeHash(joinPoint.getArgs());

        // Bắt đầu phiên kiểm tra hoặc khởi tạo Idempotency
        IdempotencyRecord record = idempotencyService.startExecution(trimmedKey, endpoint, requestHash);

        // Trường hợp CACHE HIT: Yêu cầu trước đó đã hoàn thành thành công
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info(">> [IDEMPOTENCY] Cache Hit cho key '{}' trên endpoint '{}' -> Trả về kết quả trước đó (HTTP {})",
                    trimmedKey, endpoint, record.getResponseStatus());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> returnType = signature.getReturnType();

            if (ResponseEntity.class.isAssignableFrom(returnType)) {
                Type genericType = signature.getMethod().getGenericReturnType();
                int httpStatus = record.getResponseStatus() != null ? record.getResponseStatus() : 200;

                if (record.getResponseBody() == null || record.getResponseBody().isBlank()) {
                    return ResponseEntity.status(httpStatus).build();
                }

                if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
                    JavaType javaType = objectMapper.getTypeFactory().constructType(pt.getActualTypeArguments()[0]);
                    Object body = objectMapper.readValue(record.getResponseBody(), javaType);
                    return ResponseEntity.status(httpStatus).body(body);
                }

                Object raw = objectMapper.readValue(record.getResponseBody(), Object.class);
                return ResponseEntity.status(httpStatus).body(raw);
            }

            if (record.getResponseBody() != null && !record.getResponseBody().isBlank()) {
                return objectMapper.readValue(record.getResponseBody(), returnType);
            }
            return null;
        }

        // Trường hợp LẦN ĐẦU CHẠY: Thực thi nghiệp vụ gốc
        try {
            Object result = joinPoint.proceed();

            int statusCode = 200;
            String responseJson = "";

            if (result instanceof ResponseEntity<?> responseEntity) {
                statusCode = responseEntity.getStatusCode().value();
                if (responseEntity.getBody() != null) {
                    responseJson = objectMapper.writeValueAsString(responseEntity.getBody());
                }
            } else if (result != null) {
                responseJson = objectMapper.writeValueAsString(result);
            }

            // CHỈ lưu COMPLETED khi mã HTTP là thành công (2xx).
            // Nếu là lỗi tạm thời (4xx, 5xx) -> Giải phóng key để client được phép retry!
            if (statusCode >= 200 && statusCode < 300) {
                idempotencyService.completeExecution(trimmedKey, statusCode, responseJson);
            } else {
                idempotencyService.failExecution(trimmedKey);
            }

            return result;
        } catch (Throwable ex) {
            // Khi có lỗi phát sinh trong nghiệp vụ (exception), giải phóng key để client có thể retry
            idempotencyService.failExecution(trimmedKey);
            throw ex;
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String computeHash(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        try {
            String json = objectMapper.writeValueAsString(args);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
