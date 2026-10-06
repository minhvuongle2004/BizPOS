package com.bizpos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportResultResponse {

    private int totalRows;
    private int successCount;
    private int errorCount;

    @Builder.Default
    private List<ProductImportError> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductImportError {
        private int rowNumber;
        private String productCode;
        private String reason;
    }
}
