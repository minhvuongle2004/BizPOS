package com.bizpos.service;

import com.bizpos.dto.ProductImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProductExcelService {

    byte[] exportProductsToExcel();

    ProductImportResultResponse importProductsFromExcel(MultipartFile file);
}
