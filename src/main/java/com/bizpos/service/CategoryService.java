package com.bizpos.service;

import com.bizpos.dto.CategoryRequest;
import com.bizpos.entity.Category;

import java.util.List;

public interface CategoryService {

    /**
     * Lấy danh sách toàn bộ danh mục
     */
    List<Category> getAllCategories();

    /**
     * Lấy chi tiết danh mục theo ID
     */
    Category getCategoryById(Long id);

    /**
     * Tạo mới danh mục từ CategoryRequest DTO
     */
    Category createCategory(CategoryRequest request);

    /**
     * Cập nhật danh mục theo ID từ CategoryRequest DTO
     */
    Category updateCategory(Long id, CategoryRequest request);

    /**
     * Xóa danh mục theo ID
     */
    void deleteCategory(Long id);
}
