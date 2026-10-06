package com.bizpos.service.impl;

import com.bizpos.dto.CategoryRequest;
import com.bizpos.entity.Category;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + id));
    }

    @Override
    @Transactional
    public Category createCategory(CategoryRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên danh mục không được để trống!");
        }

        String trimmedName = request.getName().trim();

        // Kiểm tra tên danh mục đã tồn tại chưa
        if (categoryRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Tên danh mục '" + trimmedName + "' đã tồn tại!");
        }

        Category category = Category.builder()
                .name(trimmedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category updateCategory(Long id, CategoryRequest request) {
        Category existingCategory = getCategoryById(id);

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên danh mục không được để trống!");
        }

        String trimmedName = request.getName().trim();

        // Nếu tên bị thay đổi, kiểm tra xem tên mới có trùng với danh mục khác không
        if (!existingCategory.getName().equalsIgnoreCase(trimmedName)
                && categoryRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Tên danh mục '" + trimmedName + "' đã tồn tại!");
        }

        existingCategory.setName(trimmedName);
        existingCategory.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        return categoryRepository.save(existingCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);

        // Kiểm tra nếu danh mục đang có sản phẩm thì không cho xóa trực tiếp
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            throw new IllegalStateException("Không thể xóa danh mục này vì đang có sản phẩm liên kết!");
        }

        categoryRepository.delete(category);
    }
}
