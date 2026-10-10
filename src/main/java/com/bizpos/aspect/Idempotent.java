package com.bizpos.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu phương thức Controller cần áp dụng tính lũy thừa (Idempotency)
 * dựa trên Header 'Idempotency-Key' hoặc 'X-Idempotency-Key'.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Tên header HTTP được kiểm tra. Mặc định là 'Idempotency-Key'
     */
    String headerName() default "Idempotency-Key";
}
