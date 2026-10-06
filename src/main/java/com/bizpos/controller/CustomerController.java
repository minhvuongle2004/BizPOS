package com.bizpos.controller;

import com.bizpos.dto.CustomerRequest;
import com.bizpos.dto.CustomerResponse;
import com.bizpos.dto.PageResponse;
import com.bizpos.entity.Customer;
import com.bizpos.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 1. Lấy danh sách khách hàng có phân trang, hỗ trợ tìm kiếm theo họ tên hoặc SĐT qua ?keyword=...
     * GET /api/customers?page=0&size=10
     * GET /api/customers?page=0&size=10&keyword=0987
     */
    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponse>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        PageResponse<CustomerResponse> response = customerService.getCustomers(page, size, keyword);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Lấy chi tiết khách hàng theo ID
     * GET /api/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(CustomerResponse.fromEntity(customer));
    }

    /**
     * 3. Tạo mới một khách hàng
     * POST /api/customers
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@jakarta.validation.Valid @RequestBody CustomerRequest request) {
        Customer createdCustomer = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.fromEntity(createdCustomer));
    }

    /**
     * 4. Cập nhật thông tin khách hàng theo ID
     * PUT /api/customers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody CustomerRequest request) {
        Customer updatedCustomer = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(CustomerResponse.fromEntity(updatedCustomer));
    }

    /**
     * 5. Xóa khách hàng theo ID
     * DELETE /api/customers/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Xóa khách hàng thành công với ID: " + id);
        return ResponseEntity.ok(response);
    }
}
