package com.bizpos.controller;

import com.bizpos.dto.CategoryRequest;
import com.bizpos.entity.Category;
import com.bizpos.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 1. Lấy danh sách toàn bộ danh mục
     * GET /api/categories
     */
    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * 2. Lấy chi tiết một danh mục theo ID
     * GET /api/categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    /**
     * 3. Tạo mới một danh mục
     * POST /api/categories
     */
    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody CategoryRequest request) {
        Category createdCategory = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    /**
     * 4. Cập nhật một danh mục theo ID
     * PUT /api/categories/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(
            @PathVariable Long id,
            @RequestBody CategoryRequest request) {
        Category updatedCategory = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(updatedCategory);
    }

    /**
     * 5. Xóa một danh mục theo ID
     * DELETE /api/categories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Xóa danh mục thành công với ID: " + id);
        return ResponseEntity.ok(response);
    }
}
