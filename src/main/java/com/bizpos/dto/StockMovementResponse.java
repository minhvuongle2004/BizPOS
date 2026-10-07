package com.bizpos.dto;

import com.bizpos.entity.MovementType;
import com.bizpos.entity.StockMovement;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private MovementType type;
    private String typeDescription;
    private Integer quantity;
    private Integer previousStock;
    private Integer currentStock;
    private String referenceCode;
    private String reason;
    private String createdBy;
    private LocalDateTime createdAt;

    public static StockMovementResponse fromEntity(StockMovement movement) {
        if (movement == null) {
            return null;
        }

        Long prodId = movement.getProduct() != null ? movement.getProduct().getId() : null;
        String prodCode = movement.getProduct() != null ? movement.getProduct().getCode() : null;
        String prodName = movement.getProduct() != null ? movement.getProduct().getName() : null;

        return StockMovementResponse.builder()
                .id(movement.getId())
                .productId(prodId)
                .productCode(prodCode)
                .productName(prodName)
                .type(movement.getType())
                .typeDescription(movement.getType() != null ? movement.getType().getDescription() : null)
                .quantity(movement.getQuantity())
                .previousStock(movement.getPreviousStock())
                .currentStock(movement.getCurrentStock())
                .referenceCode(movement.getReferenceCode())
                .reason(movement.getReason())
                .createdBy(movement.getCreatedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
