package com.bizpos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_name, entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", length = 50, nullable = false)
    private String entityName;

    @Column(name = "entity_id", length = 100, nullable = false)
    private String entityId;

    @Column(name = "action", length = 50, nullable = false)
    private String action;

    @Column(name = "action_description", length = 150)
    private String actionDescription;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;
}
