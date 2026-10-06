package com.bizpos.service;

import com.bizpos.dto.PageResponse;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.ProductResponse;
import com.bizpos.entity.Product;

import java.util.List;

public interface ProductService {

    /**
     * Lấy danh sách sản phẩm có phân trang, kết hợp tìm kiếm theo từ khóa và lọc theo danh mục
     */
    PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId);

    /**
     * Lấy danh sách toàn bộ sản phẩm
     */
    List<Product> getAllProducts();

    /**
     * Lấy chi tiết sản phẩm theo ID
     */
    Product getProductById(Long id);

    /**
     * Tạo mới một sản phẩm
     */
    Product createProduct(ProductRequest request);

    /**
     * Cập nhật thông tin sản phẩm theo ID
     */
    Product updateProduct(Long id, ProductRequest request);

    /**
     * Xóa sản phẩm theo ID
     */
    void deleteProduct(Long id);

    /**
     * Tìm kiếm sản phẩm theo tên hoặc mã sản phẩm
     */
    List<Product> searchProducts(String keyword);

    /**
     * Lấy danh sách sản phẩm theo danh mục
     */
    List<Product> getProductsByCategory(Long categoryId);
}
