package com.bizpos.controller;

import com.bizpos.dto.PageResponse;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.ProductResponse;
import com.bizpos.entity.Product;
import com.bizpos.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 1. Lấy danh sách sản phẩm có phân trang, kết hợp tìm kiếm và lọc theo danh mục
     * GET /api/products?page=0&size=10
     * GET /api/products?page=0&size=10&keyword=cafe&categoryId=1
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        PageResponse<ProductResponse> response = productService.getProducts(page, size, keyword, categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Lấy chi tiết sản phẩm theo ID
     * GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(ProductResponse.fromEntity(product));
    }

    /**
     * 3. Tạo mới một sản phẩm
     * POST /api/products
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@jakarta.validation.Valid @RequestBody ProductRequest request) {
        Product createdProduct = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.fromEntity(createdProduct));
    }

    /**
     * 4. Cập nhật thông tin sản phẩm theo ID
     * PUT /api/products/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody ProductRequest request) {
        Product updatedProduct = productService.updateProduct(id, request);
        return ResponseEntity.ok(ProductResponse.fromEntity(updatedProduct));
    }

    /**
     * 5. Xóa sản phẩm theo ID
     * DELETE /api/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Xóa sản phẩm thành công với ID: " + id);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Lấy danh sách sản phẩm theo danh mục
     * GET /api/products/category/{categoryId}
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        List<ProductResponse> responses = productService.getProductsByCategory(categoryId).stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
