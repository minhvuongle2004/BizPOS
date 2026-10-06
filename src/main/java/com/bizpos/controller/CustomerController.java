package com.bizpos.controller;

import com.bizpos.dto.CustomerRequest;
import com.bizpos.entity.Customer;
import com.bizpos.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * 1. Lấy danh sách khách hàng (hỗ trợ tìm kiếm theo họ tên hoặc SĐT qua ?keyword=...)
     * GET /api/customers
     * GET /api/customers?keyword=0987
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getCustomers(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ResponseEntity.ok(customerService.searchCustomers(keyword));
        }
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    /**
     * 2. Lấy chi tiết khách hàng theo ID
     * GET /api/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        return ResponseEntity.ok(customer);
    }

    /**
     * 3. Tạo mới một khách hàng
     * POST /api/customers
     */
    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody CustomerRequest request) {
        Customer createdCustomer = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCustomer);
    }

    /**
     * 4. Cập nhật thông tin khách hàng theo ID
     * PUT /api/customers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @RequestBody CustomerRequest request) {
        Customer updatedCustomer = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(updatedCustomer);
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
