package com.bizpos.service;

import com.bizpos.dto.CustomerRequest;
import com.bizpos.dto.CustomerResponse;
import com.bizpos.dto.PageResponse;
import com.bizpos.entity.Customer;

import java.util.List;

public interface CustomerService {

    /**
     * Lấy danh sách khách hàng có phân trang, kết hợp tìm kiếm theo họ tên hoặc SĐT
     */
    PageResponse<CustomerResponse> getCustomers(int page, int size, String keyword);

    /**
     * Lấy danh sách toàn bộ khách hàng
     */
    List<Customer> getAllCustomers();

    /**
     * Lấy chi tiết khách hàng theo ID
     */
    Customer getCustomerById(Long id);

    /**
     * Tạo mới một khách hàng
     */
    Customer createCustomer(CustomerRequest request);

    /**
     * Cập nhật thông tin khách hàng theo ID
     */
    Customer updateCustomer(Long id, CustomerRequest request);

    /**
     * Xóa khách hàng theo ID
     */
    void deleteCustomer(Long id);

    /**
     * Tìm kiếm khách hàng theo họ tên hoặc số điện thoại
     */
    List<Customer> searchCustomers(String keyword);
}
