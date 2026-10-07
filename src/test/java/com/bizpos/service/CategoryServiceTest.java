package com.bizpos.service;

import com.bizpos.dto.CategoryRequest;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho CategoryService")
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = Category.builder()
                .id(1L)
                .name("Đồ uống")
                .description("Các loại nước giải khát, cà phê, trà")
                .products(new ArrayList<>())
                .build();
    }

    // =========================================================================
    // 1. TẠO DANH MỤC (CREATE CATEGORY)
    // =========================================================================

    @Test
    @DisplayName("Tạo danh mục thành công khi tên hợp lệ và chưa tồn tại")
    void createCategory_shouldSucceed_whenDataIsValid() {
        CategoryRequest request = CategoryRequest.builder()
                .name("  Đồ ăn vặt  ")
                .description("Bánh kẹo các loại")
                .build();

        when(categoryRepository.existsByName("Đồ ăn vặt")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        Category created = categoryService.createCategory(request);

        assertNotNull(created);
        assertEquals("Đồ ăn vặt", created.getName(), "Tên danh mục phải được trim()");
        assertEquals("Bánh kẹo các loại", created.getDescription());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục thất bại khi tên danh mục đã tồn tại -> ném DuplicateResourceException")
    void createCategory_shouldThrowDuplicateResourceException_whenNameAlreadyExists() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Đồ uống")
                .description("Mô tả khác")
                .build();

        when(categoryRepository.existsByName("Đồ uống")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(
                DuplicateResourceException.class,
                () -> categoryService.createCategory(request)
        );

        assertTrue(ex.getMessage().contains("Đồ uống"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục thất bại khi tên danh mục là null hoặc rỗng -> ném IllegalArgumentException")
    void createCategory_shouldThrowIllegalArgumentException_whenNameIsEmptyOrNull() {
        CategoryRequest nullNameReq = CategoryRequest.builder().name(null).build();
        CategoryRequest blankNameReq = CategoryRequest.builder().name("   ").build();

        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(nullNameReq));
        assertThrows(IllegalArgumentException.class, () -> categoryService.createCategory(blankNameReq));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    // =========================================================================
    // 2. CẬP NHẬT DANH MỤC (UPDATE CATEGORY)
    // =========================================================================

    @Test
    @DisplayName("Cập nhật danh mục thành công khi đổi tên mới không trùng")
    void updateCategory_shouldSucceed_whenDataIsValid() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Thức uống & Trà sữa")
                .description("Cập nhật mô tả mới")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.existsByNameAndIdNot("Thức uống & Trà sữa", 1L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category updated = categoryService.updateCategory(1L, request);

        assertEquals("Thức uống & Trà sữa", updated.getName());
        assertEquals("Cập nhật mô tả mới", updated.getDescription());
        verify(categoryRepository).save(sampleCategory);
    }

    @Test
    @DisplayName("Cập nhật danh mục giữ nguyên tên cũ thì không bị báo trùng")
    void updateCategory_shouldAllowKeepingSameName() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Đồ uống") // Tên giữ nguyên
                .description("Mô tả mới")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category updated = categoryService.updateCategory(1L, request);

        assertEquals("Đồ uống", updated.getName());
        assertEquals("Mô tả mới", updated.getDescription());
        verify(categoryRepository, never()).existsByNameAndIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("Cập nhật danh mục thất bại khi tên mới trùng với danh mục khác -> ném DuplicateResourceException")
    void updateCategory_shouldThrowDuplicateResourceException_whenUpdatingToExistingNameOfAnotherCategory() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Bánh ngọt")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.existsByNameAndIdNot("Bánh ngọt", 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> categoryService.updateCategory(1L, request));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Cập nhật danh mục thất bại khi không tìm thấy ID -> ném ResourceNotFoundException")
    void updateCategory_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        CategoryRequest request = CategoryRequest.builder().name("Tên mới").build();

        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(999L, request));
    }

    @Test
    @DisplayName("Cập nhật danh mục thất bại khi tên là rỗng -> ném IllegalArgumentException")
    void updateCategory_shouldThrowIllegalArgumentException_whenNameIsEmptyOrNull() {
        CategoryRequest request = CategoryRequest.builder().name("   ").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));

        assertThrows(IllegalArgumentException.class, () -> categoryService.updateCategory(1L, request));
    }

    // =========================================================================
    // 3. XÓA DANH MỤC (DELETE CATEGORY)
    // =========================================================================

    @Test
    @DisplayName("Xóa danh mục thành công khi danh mục không chứa sản phẩm nào")
    void deleteCategory_shouldSucceed_whenCategoryHasNoProducts() {
        sampleCategory.setProducts(new ArrayList<>());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(sampleCategory);
    }

    @Test
    @DisplayName("Xóa danh mục thất bại khi danh mục không tồn tại -> ném ResourceNotFoundException")
    void deleteCategory_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(999L));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    @DisplayName("Xóa danh mục thất bại khi danh mục đang có sản phẩm liên kết -> ném IllegalStateException (409)")
    void deleteCategory_shouldThrowIllegalStateException_whenCategoryHasProducts() {
        Product linkedProduct = Product.builder().id(101L).name("Cà phê").build();
        sampleCategory.setProducts(List.of(linkedProduct));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> categoryService.deleteCategory(1L)
        );

        assertTrue(ex.getMessage().contains("đang có sản phẩm liên kết"));
        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
