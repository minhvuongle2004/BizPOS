package com.bizpos.entity;

import com.bizpos.enums.ReturnReason;
import com.bizpos.enums.ReturnStatus;
import com.bizpos.enums.ReturnType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_code", length = 50, nullable = false, unique = true)
    private String returnCode;

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", length = 30, nullable = false)
    private ReturnType returnType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.COMPLETED;

    @Column(name = "total_refund_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalRefundAmount = BigDecimal.ZERO;

    @Column(name = "total_exchange_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalExchangeAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 50, nullable = false)
    private ReturnReason reason;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "performed_by", length = 50, nullable = false)
    private String performedBy;

    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderReturnItem> returnItems = new ArrayList<>();

    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderExchangeItem> exchangeItems = new ArrayList<>();

    public void addReturnItem(OrderReturnItem item) {
        returnItems.add(item);
        item.setOrderReturn(this);
    }

    public void addExchangeItem(OrderExchangeItem item) {
        exchangeItems.add(item);
        item.setOrderReturn(this);
    }
}
