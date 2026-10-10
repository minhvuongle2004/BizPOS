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
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final com.bizpos.service.StockMovementService stockMovementService;
    private final com.bizpos.repository.ProductVariantRepository productVariantRepository;

    @Override
    public PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId, String productSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("id").descending());
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cleanSize = (productSize != null && !productSize.trim().isEmpty()) ? productSize.trim() : null;

        Page<Product> productPage = productRepository.searchProducts(cleanKeyword, categoryId, cleanSize, pageable);
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntity);

        return PageResponse.from(responsePage);
    }

    @Override
    public PageResponse<ProductResponse> getProducts(int page, int size, String keyword, Long categoryId) {
        return getProducts(page, size, keyword, categoryId, null);
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
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .size(request.getSize() != null && !request.getSize().trim().isEmpty() ? request.getSize().trim() : null)
                .color(request.getColor() != null && !request.getColor().trim().isEmpty() ? request.getColor().trim() : null)
                .material(request.getMaterial() != null && !request.getMaterial().trim().isEmpty() ? request.getMaterial().trim() : null)
                .category(category)
                .build();

        Product saved = productRepository.save(product);

        // Khởi tạo các biến thể (ProductVariants) tương ứng
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (com.bizpos.dto.ProductVariantRequest vReq : request.getVariants()) {
                String size = vReq.getSize() != null && !vReq.getSize().trim().isEmpty() ? vReq.getSize().trim() : saved.getSize();
                String color = vReq.getColor() != null && !vReq.getColor().trim().isEmpty() ? vReq.getColor().trim() : saved.getColor();
                String sku = (vReq.getSku() != null && !vReq.getSku().isBlank())
                        ? vReq.getSku().trim()
                        : generateVariantSku(saved.getCode(), size, color);
                String barcode = (vReq.getBarcode() != null && !vReq.getBarcode().isBlank())
                        ? vReq.getBarcode().trim()
                        : generateUniqueBarcode();
                BigDecimal price = vReq.getPrice() != null ? vReq.getPrice() : saved.getPrice();
                int stock = vReq.getStockQuantity() != null ? vReq.getStockQuantity() : 0;

                com.bizpos.entity.ProductVariant variant = com.bizpos.entity.ProductVariant.builder()
                        .product(saved)
                        .sku(sku)
                        .barcode(barcode)
                        .size(size)
                        .color(color)
                        .price(price)
                        .costPrice(vReq.getCostPrice())
                        .stockQuantity(stock)
                        .isActive(vReq.getIsActive() != null ? vReq.getIsActive() : true)
                        .build();

                productVariantRepository.save(variant);
            }
        } else if ((request.getSizes() != null && !request.getSizes().isEmpty()) || (request.getColors() != null && !request.getColors().isEmpty())) {
            java.util.List<String> sizes = (request.getSizes() != null && !request.getSizes().isEmpty())
                    ? request.getSizes() : java.util.List.of(saved.getSize() != null ? saved.getSize() : "Freesize");
            java.util.List<String> colors = (request.getColors() != null && !request.getColors().isEmpty())
                    ? request.getColors() : java.util.List.of(saved.getColor() != null ? saved.getColor() : "Mặc định");

            for (String size : sizes) {
                for (String color : colors) {
                    String sku = generateVariantSku(saved.getCode(), size, color);
                    String barcode = generateUniqueBarcode();
                    com.bizpos.entity.ProductVariant variant = com.bizpos.entity.ProductVariant.builder()
                            .product(saved)
                            .sku(sku)
                            .barcode(barcode)
                            .size(size != null ? size.trim() : null)
                            .color(color != null ? color.trim() : null)
                            .price(saved.getPrice())
                            .stockQuantity(saved.getStockQuantity() != null ? saved.getStockQuantity() : 0)
                            .isActive(true)
                            .build();
                    productVariantRepository.save(variant);
                }
            }
        } else {
            // Tạo 1 biến thể mặc định
            com.bizpos.entity.ProductVariant defaultVariant = com.bizpos.entity.ProductVariant.builder()
                    .product(saved)
                    .sku(saved.getCode())
                    .barcode(saved.getCode())
                    .size(saved.getSize())
                    .color(saved.getColor())
                    .price(saved.getPrice())
                    .stockQuantity(saved.getStockQuantity() != null ? saved.getStockQuantity() : 0)
                    .isActive(true)
                    .build();
            productVariantRepository.save(defaultVariant);
        }

        // Ghi nhận nhập kho ban đầu nếu stockQuantity > 0
        if (saved.getStockQuantity() != null && saved.getStockQuantity() > 0) {
            stockMovementService.recordMovement(
                    saved,
                    com.bizpos.entity.MovementType.IMPORT,
                    saved.getStockQuantity(),
                    0,
                    saved.getStockQuantity(),
                    "INIT-STOCK",
                    "Khởi tạo số lượng tồn kho ban đầu",
                    getCurrentUsername()
            );
        }

        return saved;
    }

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "UPDATE_PRODUCT", entity = "Product")
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
        if (request.getStockQuantity() != null) {
            existingProduct.setStockQuantity(request.getStockQuantity());
        }
        existingProduct.setSize(request.getSize() != null && !request.getSize().trim().isEmpty() ? request.getSize().trim() : null);
        existingProduct.setColor(request.getColor() != null && !request.getColor().trim().isEmpty() ? request.getColor().trim() : null);
        existingProduct.setMaterial(request.getMaterial() != null && !request.getMaterial().trim().isEmpty() ? request.getMaterial().trim() : null);
        existingProduct.setCategory(category);

        return productRepository.save(existingProduct);
    }

    public static final int STAFF_MAX_ADJUSTMENT_DELTA = 10;

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "ADJUST_STOCK", entity = "Product")
    public Product updateStock(Long id, Integer quantity) {
        return updateStock(id, quantity, "Điều chỉnh tồn kho kiểm kê định kỳ");
    }

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "ADJUST_STOCK", entity = "Product")
    public Product updateStock(Long id, Integer quantity, String reason) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Số lượng tồn kho phải là số nguyên không âm (>= 0)!");
        }
        if (reason == null || reason.trim().length() < 3) {
            throw new IllegalArgumentException("Vui lòng cung cấp lý do điều chỉnh tồn kho hợp lệ (tối thiểu 3 ký tự)!");
        }

        Product existingProduct = getProductById(id);
        int prevStock = existingProduct.getStockQuantity() != null ? existingProduct.getStockQuantity() : 0;
        int delta = Math.abs(quantity - prevStock);

        // Kiểm tra phân quyền: Nhân viên (STAFF) không được điều chỉnh chênh lệch lớn (> 10 cái) mà không có Quản lý
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isStaff = auth != null && auth.isAuthenticated() &&
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF")) &&
                auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isStaff && delta > STAFF_MAX_ADJUSTMENT_DELTA) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Chênh lệch điều chỉnh tồn kho (" + delta + " cái) vượt quá hạn mức cho phép của Nhân viên (tối đa " +
                    STAFF_MAX_ADJUSTMENT_DELTA + " cái). Yêu cầu Quản lý (ADMIN) thực hiện hoặc phê duyệt!");
        }

        existingProduct.setStockQuantity(quantity);
        Product updated = productRepository.save(existingProduct);

        if (quantity != prevStock) {
            String note = reason.trim() + " (" + (quantity > prevStock ? "+" : "-") + delta + ")";
            stockMovementService.recordMovement(
                    updated,
                    com.bizpos.entity.MovementType.ADJUSTMENT,
                    delta,
                    prevStock,
                    quantity,
                    "ADJUST-" + id,
                    note,
                    getCurrentUsername()
            );
        }

        return updated;
    }

    private String getCurrentUsername() {
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    @Override
    @Transactional
    @com.bizpos.aspect.Auditable(action = "DELETE_PRODUCT", entity = "Product")
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

    @Override
    @Transactional
    public com.bizpos.entity.ProductVariant addVariant(Long productId, com.bizpos.dto.ProductVariantRequest request) {
        Product product = getProductById(productId);
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu biến thể không được để trống!");
        }

        String size = request.getSize() != null && !request.getSize().trim().isEmpty() ? request.getSize().trim() : null;
        String color = request.getColor() != null && !request.getColor().trim().isEmpty() ? request.getColor().trim() : null;

        String sku = (request.getSku() != null && !request.getSku().isBlank())
                ? request.getSku().trim()
                : generateVariantSku(product.getCode(), size, color);

        if (productVariantRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("Mã SKU '" + sku + "' đã tồn tại!");
        }

        String barcode = (request.getBarcode() != null && !request.getBarcode().isBlank())
                ? request.getBarcode().trim()
                : generateUniqueBarcode();

        if (productVariantRepository.existsByBarcode(barcode)) {
            throw new DuplicateResourceException("Mã Barcode '" + barcode + "' đã tồn tại!");
        }

        BigDecimal price = request.getPrice() != null ? request.getPrice() : product.getPrice();
        int stock = request.getStockQuantity() != null ? request.getStockQuantity() : 0;

        com.bizpos.entity.ProductVariant variant = com.bizpos.entity.ProductVariant.builder()
                .product(product)
                .sku(sku)
                .barcode(barcode)
                .size(size)
                .color(color)
                .price(price)
                .costPrice(request.getCostPrice())
                .stockQuantity(stock)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        return productVariantRepository.save(variant);
    }

    @Override
    public List<com.bizpos.entity.ProductVariant> getVariantsByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
        return productVariantRepository.findByProductId(productId);
    }

    @Override
    public com.bizpos.entity.ProductVariant getVariantById(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + variantId));
    }

    @Override
    public com.bizpos.entity.ProductVariant getVariantByBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã barcode không được để trống!");
        }
        return productVariantRepository.findByBarcodeWithProduct(barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với mã Barcode: " + barcode));
    }

    @Override
    public List<com.bizpos.entity.ProductVariant> searchVariants(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return productVariantRepository.searchActiveVariants(keyword.trim());
    }

    private String generateVariantSku(String baseCode, String size, String color) {
        String s = (size != null && !size.isBlank()) ? size.replaceAll("\\s+", "").toUpperCase() : "DEF";
        String c = (color != null && !color.isBlank()) ? color.replaceAll("\\s+", "").toUpperCase() : "DEF";
        return baseCode + "-" + c + "-" + s;
    }

    private String generateUniqueBarcode() {
        String barcode;
        do {
            long rnd = Math.abs(System.nanoTime() + java.util.concurrent.ThreadLocalRandom.current().nextLong(1000000));
            barcode = "893" + String.format("%09d", rnd % 1000000000L);
        } while (productVariantRepository.existsByBarcode(barcode));
        return barcode;
    }
}
