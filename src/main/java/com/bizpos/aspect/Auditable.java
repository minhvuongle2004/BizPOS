package com.bizpos.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Tên hành động nghiệp vụ (ví dụ: UPDATE_PRODUCT, DELETE_PRODUCT, UPDATE_ORDER, DELETE_ORDER)
     */
    String action();

    /**
     * Tên đối tượng bị tác động (ví dụ: Product, Order)
     */
    String entity();
}
