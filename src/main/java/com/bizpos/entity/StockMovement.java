package com.bizpos.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movement_product", columnList = "product_id"),
        @Index(name = "idx_stock_movement_created_at", columnList = "created_at")
})
@org.hibernate.annotations.Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", updatable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 30, nullable = false, updatable = false)
    private MovementType type;

    /**
     * Số lượng biến động tuyệt đối:
     */
    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "previous_stock", nullable = false, updatable = false)
    private Integer previousStock;

    @Column(name = "current_stock", nullable = false, updatable = false)
    private Integer currentStock;

    @Column(name = "reference_code", length = 100, updatable = false)
    private String referenceCode;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    /**
     * Độ biến động có dấu (Delta) = currentStock - previousStock:
     * - Âm khi xuất bán (ví dụ: -2)
     * - Dương khi nhập hàng, hoàn hàng (ví dụ: +5)
     * - Âm hoặc dương khi điều chỉnh kho (ADJUSTMENT)
     */
    public int getSignedDelta() {
        return (currentStock != null ? currentStock : 0) - (previousStock != null ? previousStock : 0);
    }
}
