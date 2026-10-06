package com.bizpos.service.impl;

import com.bizpos.dto.PageResponse;
import com.bizpos.dto.ProductRequest;
import com.bizpos.dto.ProductResponse;
import com.bizpos.entity.Category;
import com.bizpos.entity.Product;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CategoryRepository;
import com.bizpos.repository.OrderItemRepository;
import com.bizpos.repository.ProductRepository;
import com.bizpos.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    public PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("id").descending());
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Product> productPage = productRepository.searchProducts(cleanKeyword, categoryId, pageable);
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntity);

        return PageResponse.from(responsePage);
    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
    }

    @Override
    @Transactional
    public Product createProduct(ProductRequest request) {
        validateProductRequest(request);

        String trimmedCode = request.getCode().trim();

        // 1. Kiểm tra mã sản phẩm không được trùng
        if (productRepository.existsByCode(trimmedCode)) {
            throw new DuplicateResourceException("Mã sản phẩm '" + trimmedCode + "' đã tồn tại!");
        }

        // 2. Kiểm tra categoryId phải tồn tại
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        Product product = Product.builder()
                .code(trimmedCode)
                .name(request.getName().trim())
                .price(request.getPrice())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .category(category)
                .build();

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        Product existingProduct = getProductById(id);
        validateProductRequest(request);

        String trimmedCode = request.getCode().trim();

        // 1. Kiểm tra mã sản phẩm không trùng với sản phẩm khác
        if (!existingProduct.getCode().equalsIgnoreCase(trimmedCode)
                && productRepository.existsByCodeAndIdNot(trimmedCode, id)) {
            throw new DuplicateResourceException("Mã sản phẩm '" + trimmedCode + "' đã tồn tại!");
        }

        // 2. Kiểm tra categoryId phải tồn tại
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        existingProduct.setCode(trimmedCode);
        existingProduct.setName(request.getName().trim());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        existingProduct.setCategory(category);

        return productRepository.save(existingProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);

        // Kiểm tra xem sản phẩm đã có trong chi tiết đơn hàng chưa
        if (orderItemRepository.existsByProductId(id)) {
            throw new IllegalStateException("Không thể xóa sản phẩm này vì đã tồn tại trong đơn hàng!");
        }

        productRepository.delete(product);
    }

    @Override
    public List<Product> searchProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return productRepository.findAll();
        }
        String trimmed = keyword.trim();
        return productRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(trimmed, trimmed);
    }

    @Override
    public List<Product> getProductsByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + categoryId);
        }
        return productRepository.findByCategoryId(categoryId);
    }

    /**
     * Hàm dùng chung kiểm tra tính hợp lệ cơ bản của dữ liệu đầu vào
     */
    private void validateProductRequest(ProductRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Mã sản phẩm không được để trống!");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống!");
        }
        // 3. Kiểm tra price phải lớn hơn 0
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá bán sản phẩm phải lớn hơn 0!");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn danh mục cho sản phẩm!");
        }
    }
}
