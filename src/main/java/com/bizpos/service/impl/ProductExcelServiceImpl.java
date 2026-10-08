package com.bizpos.service.impl;

import com.bizpos.dto.ProductImportResultResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.ProductExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelServiceImpl implements ProductExcelService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final com.bizpos.service.StockMovementService stockMovementService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProductsToExcel() {
        List<Product> products = productRepository.findAllWithCategory();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sản phẩm");
            sheet.setDisplayGridlines(true);

            // 1. Tạo Cell Styles
            // Header Style: Nền Crimson Ruby (#E11D48), chữ trắng đậm
            byte[] crimsonRgb = new byte[]{(byte) 225, (byte) 29, (byte) 72};
            XSSFColor headerColor = new XSSFColor(crimsonRgb, null);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Segoe UI");
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) headerStyle).setFillForegroundColor(headerColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Data Styles
            Font dataFont = workbook.createFont();
            dataFont.setFontName("Segoe UI");
            dataFont.setFontHeightInPoints((short) 10);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setFont(dataFont);
            textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(textStyle);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setFont(dataFont);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(centerStyle);

            CellStyle priceStyle = workbook.createCellStyle();
            priceStyle.setFont(dataFont);
            priceStyle.setAlignment(HorizontalAlignment.RIGHT);
            priceStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            DataFormat dataFormat = workbook.createDataFormat();
            priceStyle.setDataFormat(dataFormat.getFormat("#,##0"));
            setBorders(priceStyle);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setFont(dataFont);
            numberStyle.setAlignment(HorizontalAlignment.CENTER);
            numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(numberStyle);

            // 2. Viết Header Row
            String[] headers = {
                    "Mã sản phẩm",
                    "Tên sản phẩm",
                    "Danh mục",
                    "Kích cỡ",
                    "Màu sắc",
                    "Chất liệu",
                    "Giá bán (VNĐ)",
                    "Tồn kho",
                    "Ngày tạo"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 3. Viết Data Rows
            int rowIndex = 1;
            for (Product p : products) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(22);

                // Cột 0: Mã sản phẩm
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(p.getCode() != null ? p.getCode() : "");
                cell0.setCellStyle(centerStyle);

                // Cột 1: Tên sản phẩm
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(p.getName() != null ? p.getName() : "");
                cell1.setCellStyle(textStyle);

                // Cột 2: Danh mục
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(p.getCategory() != null ? p.getCategory().getName() : "—");
                cell2.setCellStyle(textStyle);

                // Cột 3: Kích cỡ
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(p.getSize() != null ? p.getSize() : "—");
                cell3.setCellStyle(centerStyle);

                // Cột 4: Màu sắc
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(p.getColor() != null ? p.getColor() : "—");
                cell4.setCellStyle(centerStyle);

                // Cột 5: Chất liệu
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(p.getMaterial() != null ? p.getMaterial() : "—");
                cell5.setCellStyle(textStyle);

                // Cột 6: Giá bán (VNĐ)
                Cell cell6 = row.createCell(6);
                if (p.getPrice() != null) {
                    cell6.setCellValue(p.getPrice().doubleValue());
                } else {
                    cell6.setCellValue(0);
                }
                cell6.setCellStyle(priceStyle);

                // Cột 7: Tồn kho
                Cell cell7 = row.createCell(7);
                cell7.setCellValue(p.getStockQuantity() != null ? p.getStockQuantity() : 0);
                cell7.setCellStyle(numberStyle);

                // Cột 8: Ngày tạo
                Cell cell8 = row.createCell(8);
                String createdAtStr = p.getCreatedAt() != null ? p.getCreatedAt().format(DATE_TIME_FORMATTER) : "—";
                cell8.setCellValue(createdAtStr);
                cell8.setCellStyle(centerStyle);
            }


            // 4. Auto-fit column widths
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.max(currentWidth + 1200, 3800));
            }

            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            log.error("Lỗi khi tạo file Excel xuất sản phẩm: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi xuất file Excel sản phẩm: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ProductImportResultResponse importProductsFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng tải lên một file Excel!");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Định dạng file không hợp lệ! Vui lòng chỉ tải lên file Excel định dạng .xlsx");
        }

        // Cache danh mục theo tên chữ thường để tra cứu nhanh
        Map<String, Category> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(
                        c -> c.getName().trim().toLowerCase(),
                        c -> c,
                        (existing, replacement) -> existing
                ));

        // Cache danh sách mã sản phẩm đã tồn tại trong DB
        Set<String> existingCodes = productRepository.findAll().stream()
                .map(p -> p.getCode().trim().toLowerCase())
                .collect(Collectors.toSet());

        Set<String> fileCodes = new HashSet<>();
        List<Product> productsToSave = new ArrayList<>();
        List<ProductImportResultResponse.ProductImportError> errors = new ArrayList<>();

        int totalDataRows = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return ProductImportResultResponse.builder()
                        .totalRows(0)
                        .successCount(0)
                        .errorCount(0)
                        .errors(Collections.emptyList())
                        .build();
            }

            int lastRowNum = sheet.getLastRowNum();

            Row headerRow = sheet.getRow(0);
            boolean hasFashionColumns = false;
            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String h = getCellString(headerRow.getCell(c)).toLowerCase();
                    if (h.contains("size") || h.contains("cỡ") || h.contains("màu") || h.contains("color")) {
                        hasFashionColumns = true;
                        break;
                    }
                }
            }

            // Duyệt từng dòng (bỏ qua dòng Header index 0)
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue; // Bỏ qua các dòng hoàn toàn trống
                }

                totalDataRows++;
                int displayRowNumber = r + 1; // 1-based theo số dòng thực tế trong Excel

                String code = getCellString(row.getCell(0));
                String name = getCellString(row.getCell(1));
                String categoryName = getCellString(row.getCell(2));
                String size = null;
                String color = null;
                String material = null;
                BigDecimal price;
                Integer stock;

                if (hasFashionColumns) {
                    size = getCellString(row.getCell(3));
                    color = getCellString(row.getCell(4));
                    material = getCellString(row.getCell(5));
                    price = getCellBigDecimal(row.getCell(6));
                    stock = getCellInteger(row.getCell(7));
                } else {
                    price = getCellBigDecimal(row.getCell(3));
                    stock = getCellInteger(row.getCell(4));
                }

                List<String> rowErrors = new ArrayList<>();

                // 1. Kiểm tra Mã sản phẩm
                if (code.isEmpty()) {
                    rowErrors.add("Mã sản phẩm không được để trống");
                } else {
                    String codeLower = code.toLowerCase();
                    if (existingCodes.contains(codeLower)) {
                        rowErrors.add("Mã sản phẩm '" + code + "' đã tồn tại trong hệ thống");
                    } else if (fileCodes.contains(codeLower)) {
                        rowErrors.add("Mã sản phẩm '" + code + "' bị trùng lặp trong file import");
                    }
                }

                // 2. Kiểm tra Tên sản phẩm
                if (name.isEmpty()) {
                    rowErrors.add("Tên sản phẩm không được để trống");
                }

                // 3. Kiểm tra Danh mục
                Category matchedCategory = null;
                if (categoryName.isEmpty()) {
                    rowErrors.add("Danh mục không được để trống");
                } else {
                    matchedCategory = categoryMap.get(categoryName.toLowerCase());
                    if (matchedCategory == null) {
                        rowErrors.add("Danh mục '" + categoryName + "' không tồn tại trong hệ thống");
                    }
                }

                // 4. Kiểm tra Giá
                if (price == null) {
                    rowErrors.add("Giá sản phẩm không hợp lệ hoặc để trống");
                } else if (price.compareTo(BigDecimal.ZERO) < 0) {
                    rowErrors.add("Giá sản phẩm không được nhỏ hơn 0");
                }

                // 5. Kiểm tra Tồn kho
                if (stock == null) {
                    rowErrors.add("Số lượng tồn kho không hợp lệ");
                } else if (stock < 0) {
                    rowErrors.add("Số lượng tồn kho không được nhỏ hơn 0");
                }

                // Đánh giá kết quả dòng
                if (!rowErrors.isEmpty()) {
                    errors.add(ProductImportResultResponse.ProductImportError.builder()
                            .rowNumber(displayRowNumber)
                            .productCode(!code.isEmpty() ? code : "—")
                            .reason(String.join("; ", rowErrors))
                            .build());
                } else {
                    fileCodes.add(code.toLowerCase());
                    Product newProduct = Product.builder()
                            .code(code)
                            .name(name)
                            .category(matchedCategory)
                            .size(size != null && !size.trim().isEmpty() ? size.trim() : null)
                            .color(color != null && !color.trim().isEmpty() ? color.trim() : null)
                            .material(material != null && !material.trim().isEmpty() ? material.trim() : null)
                            .price(price)
                            .stockQuantity(stock)
                            .description("Nhập từ file Excel")
                            .build();
                    productsToSave.add(newProduct);
                }
            }

            // Lưu toàn bộ các sản phẩm hợp lệ vào database
            if (!productsToSave.isEmpty()) {
                List<Product> savedProducts = productRepository.saveAll(productsToSave);
                for (Product sp : savedProducts) {
                    if (sp.getStockQuantity() != null && sp.getStockQuantity() > 0) {
                        stockMovementService.recordMovement(
                                sp,
                                com.bizpos.entity.MovementType.IMPORT,
                                sp.getStockQuantity(),
                                0,
                                sp.getStockQuantity(),
                                "EXCEL-IMPORT",
                                "Nhập hàng từ file Excel: " + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "danh_sach.xlsx"),
                                getCurrentUsername()
                        );
                    }
                }
                log.info("Import thành công {} sản phẩm từ Excel", productsToSave.size());
            }

        } catch (IOException e) {
            log.error("Lỗi khi đọc file Excel import: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể đọc nội dung file Excel: " + e.getMessage(), e);
        }

        return ProductImportResultResponse.builder()
                .totalRows(totalDataRows)
                .successCount(productsToSave.size())
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    private String getCurrentUsername() {
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    private void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(cell);
                if (!val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) return null;
                s = s.replaceAll("[\\sđ₫VNDvnd]", "");
                if (s.matches("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$")) {
                    s = s.replace(",", "");
                } else if (s.matches("^\\d{1,3}(\\.\\d{3})+(,\\d+)?$")) {
                    s = s.replace(".", "").replace(",", ".");
                }
                try {
                    return new BigDecimal(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private Integer getCellInteger(Cell cell) {
        if (cell == null) return 0;
        CellType type = cell.getCellType();
        if (type == CellType.BLANK) return 0;
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case NUMERIC:
                return (int) Math.round(cell.getNumericCellValue());
            case STRING:
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) return 0;
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }
}
