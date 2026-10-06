package com.bizpos.service.impl;

import com.bizpos.dto.CustomerRequest;
import com.bizpos.dto.CustomerResponse;
import com.bizpos.dto.PageResponse;
import com.bizpos.entity.Customer;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final CustomerRepository customerRepository;

    @Override
    public PageResponse<CustomerResponse> getCustomers(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("id").descending());
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<Customer> customerPage = customerRepository.searchCustomers(cleanKeyword, pageable);
        Page<CustomerResponse> responsePage = customerPage.map(CustomerResponse::fromEntity);

        return PageResponse.from(responsePage);
    }

    @Override
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Override
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + id));
    }

    @Override
    @Transactional
    public Customer createCustomer(CustomerRequest request) {
        validateCustomerRequest(request, null);

        String trimmedFullName = request.getFullName().trim();
        String trimmedPhone = (request.getPhone() != null && !request.getPhone().trim().isEmpty()) 
                ? request.getPhone().trim() : null;
        String trimmedEmail = (request.getEmail() != null && !request.getEmail().trim().isEmpty()) 
                ? request.getEmail().trim() : null;
        String trimmedAddress = (request.getAddress() != null && !request.getAddress().trim().isEmpty()) 
                ? request.getAddress().trim() : null;

        Customer customer = Customer.builder()
                .fullName(trimmedFullName)
                .phone(trimmedPhone)
                .email(trimmedEmail)
                .address(trimmedAddress)
                .build();

        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest request) {
        Customer existingCustomer = getCustomerById(id);
        validateCustomerRequest(request, id);

        String trimmedFullName = request.getFullName().trim();
        String trimmedPhone = (request.getPhone() != null && !request.getPhone().trim().isEmpty()) 
                ? request.getPhone().trim() : null;
        String trimmedEmail = (request.getEmail() != null && !request.getEmail().trim().isEmpty()) 
                ? request.getEmail().trim() : null;
        String trimmedAddress = (request.getAddress() != null && !request.getAddress().trim().isEmpty()) 
                ? request.getAddress().trim() : null;

        existingCustomer.setFullName(trimmedFullName);
        existingCustomer.setPhone(trimmedPhone);
        existingCustomer.setEmail(trimmedEmail);
        existingCustomer.setAddress(trimmedAddress);

        return customerRepository.save(existingCustomer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }

    @Override
    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAll();
        }
        String trimmed = keyword.trim();
        return customerRepository.findByFullNameContainingIgnoreCaseOrPhoneContaining(trimmed, trimmed);
    }

    /**
     * Kiểm tra tính hợp lệ của thông tin khách hàng:
     * - fullName không được để trống
     * - phone nếu có thì không được trùng
     * - email nếu có thì không được trùng và đúng định dạng
     */
    private void validateCustomerRequest(CustomerRequest request, Long existingId) {
        // 1. fullName không được để trống
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Họ và tên khách hàng không được để trống!");
        }

        // 2. phone nếu có thì không được trùng
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String phone = request.getPhone().trim();
            if (existingId == null) {
                if (customerRepository.existsByPhone(phone)) {
                    throw new DuplicateResourceException("Số điện thoại '" + phone + "' đã tồn tại!");
                }
            } else {
                if (customerRepository.existsByPhoneAndIdNot(phone, existingId)) {
                    throw new DuplicateResourceException("Số điện thoại '" + phone + "' đã tồn tại!");
                }
            }
        }

        // 3. email nếu có thì phải đúng định dạng và không được trùng
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String email = request.getEmail().trim();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("Email '" + email + "' không đúng định dạng!");
            }

            if (existingId == null) {
                if (customerRepository.existsByEmail(email)) {
                    throw new DuplicateResourceException("Email '" + email + "' đã tồn tại!");
                }
            } else {
                if (customerRepository.existsByEmailAndIdNot(email, existingId)) {
                    throw new DuplicateResourceException("Email '" + email + "' đã tồn tại!");
                }
            }
        }
    }
}
