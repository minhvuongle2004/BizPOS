package com.bizpos;

import com.bizpos.dto.ProductImportResultResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.ProductExcelService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProductExcelIntegrationTest {

    @Autowired
    private ProductExcelService productExcelService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Đồ uống")
                    .description("Danh mục thử nghiệm")
                    .build();
            return categoryRepository.save(cat);
        });
    }

    @Test
    @DisplayName("1. Test Export Products ra byte[] Excel .xlsx hợp lệ")
    void testExportProductsToExcel() throws IOException {
        byte[] excelBytes = productExcelService.exportProductsToExcel();
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0, "File Excel export phải có dung lượng > 0");

        // Đọc lại bằng POI để kiểm tra cấu trúc
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertNotNull(sheet);
            assertEquals("Sản phẩm", sheet.getSheetName());

            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("Mã sản phẩm", headerRow.getCell(0).getStringCellValue());
            assertEquals("Tên sản phẩm", headerRow.getCell(1).getStringCellValue());
            assertEquals("Danh mục", headerRow.getCell(2).getStringCellValue());
            assertEquals("Kích cỡ", headerRow.getCell(3).getStringCellValue());
            assertEquals("Màu sắc", headerRow.getCell(4).getStringCellValue());
            assertEquals("Chất liệu", headerRow.getCell(5).getStringCellValue());
            assertEquals("Giá bán (VNĐ)", headerRow.getCell(6).getStringCellValue());
            assertEquals("Tồn kho", headerRow.getCell(7).getStringCellValue());
            assertEquals("Ngày tạo", headerRow.getCell(8).getStringCellValue());
        }
    }

    @Test
    @DisplayName("2. Test Import File Excel hợp lệ và kiểm tra lưu vào MySQL DB")
    void testImportValidProducts() throws IOException {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String code1 = "VALID_SP_" + uniqueSuffix + "_1";
        String code2 = "VALID_SP_" + uniqueSuffix + "_2";

        byte[] xlsxBytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("ImportData");

            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã sản phẩm");
            header.createCell(1).setCellValue("Tên sản phẩm");
            header.createCell(2).setCellValue("Danh mục");
            header.createCell(3).setCellValue("Giá");
            header.createCell(4).setCellValue("Tồn kho");

            // Row 1
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(code1);
            r1.createCell(1).setCellValue("Trà sữa trân châu " + uniqueSuffix);
            r1.createCell(2).setCellValue(testCategory.getName());
            r1.createCell(3).setCellValue(35000);
            r1.createCell(4).setCellValue(100);

            // Row 2
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(code2);
            r2.createCell(1).setCellValue("Cà phê sữa đá " + uniqueSuffix);
            r2.createCell(2).setCellValue(testCategory.getName());
            r2.createCell(3).setCellValue(25000);
            r2.createCell(4).setCellValue(50);

            workbook.write(bos);
            xlsxBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "products_valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        ProductImportResultResponse result = productExcelService.importProductsFromExcel(file);

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getErrorCount());
        assertTrue(result.getErrors().isEmpty());

        // Kiểm tra trong DB
        Optional<Product> p1 = productRepository.findByCode(code1);
        assertTrue(p1.isPresent(), "Sản phẩm 1 phải được lưu trong DB");
        assertEquals("Trà sữa trân châu " + uniqueSuffix, p1.get().getName());
        assertEquals(new BigDecimal("35000.00"), p1.get().getPrice());
        assertEquals(100, p1.get().getStockQuantity());

        Optional<Product> p2 = productRepository.findByCode(code2);
        assertTrue(p2.isPresent(), "Sản phẩm 2 phải được lưu trong DB");
        assertEquals(50, p2.get().getStockQuantity());
    }

    @Test
    @DisplayName("3. Test Import File có dòng lỗi: trùng mã DB, danh mục không tồn tại, giá âm, tồn âm, trùng trong file")
    void testImportProductsWithErrors() throws IOException {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // Tạo 1 sản phẩm đã có trong DB
        String existingCode = "EXIST_DB_" + uniqueSuffix;
        Product existing = Product.builder()
                .code(existingCode)
                .name("Sản phẩm đã có sẵn")
                .price(BigDecimal.valueOf(10000))
                .stockQuantity(10)
                .category(testCategory)
                .build();
        productRepository.save(existing);

        String validCode = "VALID_AFTER_ERRORS_" + uniqueSuffix;
        String dupFileCode = "DUP_FILE_" + uniqueSuffix;

        byte[] xlsxBytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("ImportData");

            // Header (row 0)
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã");
            header.createCell(1).setCellValue("Tên");
            header.createCell(2).setCellValue("Danh mục");
            header.createCell(3).setCellValue("Giá");
            header.createCell(4).setCellValue("Tồn kho");

            // Row 1 (Excel line 2): Mã trống
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("");
            r1.createCell(1).setCellValue("SP Mã Trống");
            r1.createCell(2).setCellValue(testCategory.getName());
            r1.createCell(3).setCellValue(20000);
            r1.createCell(4).setCellValue(10);

            // Row 2 (Excel line 3): Trùng mã đã có trong DB
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(existingCode);
            r2.createCell(1).setCellValue("SP Trùng Code DB");
            r2.createCell(2).setCellValue(testCategory.getName());
            r2.createCell(3).setCellValue(20000);
            r2.createCell(4).setCellValue(10);

            // Row 3 (Excel line 4): Tên trống
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("EMPTY_NAME_" + uniqueSuffix);
            r3.createCell(1).setCellValue("");
            r3.createCell(2).setCellValue(testCategory.getName());
            r3.createCell(3).setCellValue(20000);
            r3.createCell(4).setCellValue(10);

            // Row 4 (Excel line 5): Danh mục không tồn tại
            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("INVALID_CAT_" + uniqueSuffix);
            r4.createCell(1).setCellValue("SP Danh Mục Lạ");
            r4.createCell(2).setCellValue("DanhMucKhongTonTai_9999");
            r4.createCell(3).setCellValue(20000);
            r4.createCell(4).setCellValue(10);

            // Row 5 (Excel line 6): Giá âm
            Row r5 = sheet.createRow(5);
            r5.createCell(0).setCellValue("NEG_PRICE_" + uniqueSuffix);
            r5.createCell(1).setCellValue("SP Giá Âm");
            r5.createCell(2).setCellValue(testCategory.getName());
            r5.createCell(3).setCellValue(-15000);
            r5.createCell(4).setCellValue(10);

            // Row 6 (Excel line 7): Tồn kho âm
            Row r6 = sheet.createRow(6);
            r6.createCell(0).setCellValue("NEG_STOCK_" + uniqueSuffix);
            r6.createCell(1).setCellValue("SP Tồn Âm");
            r6.createCell(2).setCellValue(testCategory.getName());
            r6.createCell(3).setCellValue(20000);
            r6.createCell(4).setCellValue(-5);

            // Row 7 (Excel line 8): Lần đầu xuất hiện của dupFileCode -> HỢP LỆ
            Row r7 = sheet.createRow(7);
            r7.createCell(0).setCellValue(dupFileCode);
            r7.createCell(1).setCellValue("SP Duplicate First");
            r7.createCell(2).setCellValue(testCategory.getName());
            r7.createCell(3).setCellValue(50000);
            r7.createCell(4).setCellValue(20);

            // Row 8 (Excel line 9): Lần 2 xuất hiện của dupFileCode -> LỖI trùng trong file
            Row r8 = sheet.createRow(8);
            r8.createCell(0).setCellValue(dupFileCode);
            r8.createCell(1).setCellValue("SP Duplicate Second");
            r8.createCell(2).setCellValue(testCategory.getName());
            r8.createCell(3).setCellValue(50000);
            r8.createCell(4).setCellValue(20);

            // Row 9 (Excel line 10): Dòng hoàn toàn HỢP LỆ
            Row r9 = sheet.createRow(9);
            r9.createCell(0).setCellValue(validCode);
            r9.createCell(1).setCellValue("SP Hợp Lệ Hoàn Toàn");
            r9.createCell(2).setCellValue(testCategory.getName());
            r9.createCell(3).setCellValue(45000);
            r9.createCell(4).setCellValue(30);

            workbook.write(bos);
            xlsxBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "products_mixed.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        ProductImportResultResponse result = productExcelService.importProductsFromExcel(file);

        // Tổng: 9 dòng data.
        // Hợp lệ: Row 7 (dupFileCode lần 1) và Row 9 (validCode) = 2 thành công.
        // Lỗi: Row 1, 2, 3, 4, 5, 6, 8 = 7 lỗi.
        assertEquals(9, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(7, result.getErrorCount());
        assertEquals(7, result.getErrors().size());

        // Kiểm tra các dòng hợp lệ vẫn được lưu thành công vào DB dù có các dòng lỗi khác
        Optional<Product> pValid = productRepository.findByCode(validCode);
        assertTrue(pValid.isPresent(), "Sản phẩm hợp lệ dòng 9 phải được lưu vào DB dù có lỗi ở các dòng khác");
        assertEquals(30, pValid.get().getStockQuantity());

        Optional<Product> pDup1 = productRepository.findByCode(dupFileCode);
        assertTrue(pDup1.isPresent(), "Sản phẩm hợp lệ dòng 7 phải được lưu vào DB");

        // Các sản phẩm lỗi không được lưu vào DB
        assertTrue(productRepository.findByCode("INVALID_CAT_" + uniqueSuffix).isEmpty());
        assertTrue(productRepository.findByCode("NEG_PRICE_" + uniqueSuffix).isEmpty());
        assertTrue(productRepository.findByCode("NEG_STOCK_" + uniqueSuffix).isEmpty());
    }

    @Test
    @DisplayName("4. Test Import File Excel chuẩn Thời trang với Size, Màu sắc, Chất liệu")
    void testImportFashionProductsWithAttributes() throws IOException {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String poloCode = "FASHION_POLO_" + uniqueSuffix;
        String jeanCode = "FASHION_JEAN_" + uniqueSuffix;

        byte[] xlsxBytes;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("FashionImport");

            // Header Thời trang 8 cột
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã sản phẩm");
            header.createCell(1).setCellValue("Tên sản phẩm");
            header.createCell(2).setCellValue("Danh mục");
            header.createCell(3).setCellValue("Kích cỡ");
            header.createCell(4).setCellValue("Màu sắc");
            header.createCell(5).setCellValue("Chất liệu");
            header.createCell(6).setCellValue("Giá");
            header.createCell(7).setCellValue("Tồn kho");

            // Row 1: Áo Polo
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(poloCode);
            r1.createCell(1).setCellValue("Áo Polo Fashion Test");
            r1.createCell(2).setCellValue(testCategory.getName());
            r1.createCell(3).setCellValue("XL");
            r1.createCell(4).setCellValue("Xanh Navy");
            r1.createCell(5).setCellValue("Cotton Pique 100%");
            r1.createCell(6).setCellValue(320000);
            r1.createCell(7).setCellValue(40);

            // Row 2: Quần Jean
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(jeanCode);
            r2.createCell(1).setCellValue("Quần Jean Fashion Test");
            r2.createCell(2).setCellValue(testCategory.getName());
            r2.createCell(3).setCellValue("32");
            r2.createCell(4).setCellValue("Đen Xước");
            r2.createCell(5).setCellValue("Denim Spandex");
            r2.createCell(6).setCellValue(480000);
            r2.createCell(7).setCellValue(25);

            workbook.write(bos);
            xlsxBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "fashion_import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        ProductImportResultResponse result = productExcelService.importProductsFromExcel(file);

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getErrorCount());

        // Kiểm tra dữ liệu được lưu đúng size, color, material
        Optional<Product> poloOpt = productRepository.findByCode(poloCode);
        assertTrue(poloOpt.isPresent());
        Product polo = poloOpt.get();
        assertEquals("XL", polo.getSize());
        assertEquals("Xanh Navy", polo.getColor());
        assertEquals("Cotton Pique 100%", polo.getMaterial());
        assertEquals(40, polo.getStockQuantity());

        Optional<Product> jeanOpt = productRepository.findByCode(jeanCode);
        assertTrue(jeanOpt.isPresent());
        Product jean = jeanOpt.get();
        assertEquals("32", jean.getSize());
        assertEquals("Đen Xước", jean.getColor());
        assertEquals("Denim Spandex", jean.getMaterial());
        assertEquals(25, jean.getStockQuantity());
    }
}

