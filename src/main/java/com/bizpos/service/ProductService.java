package com.bizpos.service;

import com.bizpos.dto.PageResponse;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.ProductResponse;
import com.bizpos.entity.Product;

import java.util.List;

public interface ProductService {

    /**
     * Lấy danh sách sản phẩm có phân trang, kết hợp tìm kiếm theo từ khóa, lọc theo danh mục và kích cỡ
     */
    PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId, String productSize);

    default PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId) {
        return getProducts(page, size, keyword, categoryId, null);
    }

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

    /**
     * Cập nhật số lượng tồn kho của sản phẩm kèm lý do kiểm kê
     */
    Product updateStock(Long id, Integer quantity, String reason);

    /**
     * Cập nhật số lượng tồn kho với lý do mặc định
     */
    default Product updateStock(Long id, Integer quantity) {
        return updateStock(id, quantity, "Điều chỉnh tồn kho kiểm kê định kỳ");
    }

    /**
     * Thêm một biến thể mới cho sản phẩm có sẵn
     */
    com.bizpos.entity.ProductVariant addVariant(Long productId, com.bizpos.dto.ProductVariantRequest request);

    /**
     * Lấy toàn bộ biến thể của một sản phẩm
     */
    List<com.bizpos.entity.ProductVariant> getVariantsByProductId(Long productId);

    /**
     * Lấy chi tiết biến thể theo ID
     */
    com.bizpos.entity.ProductVariant getVariantById(Long variantId);

    /**
     * Tìm kiếm biến thể theo Barcode
     */
    com.bizpos.entity.ProductVariant getVariantByBarcode(String barcode);

    /**
     * Tìm kiếm biến thể hoạt động theo từ khóa (Mã SKU, Barcode, Tên sản phẩm, Mã sản phẩm)
     */
    List<com.bizpos.entity.ProductVariant> searchVariants(String keyword);
}
