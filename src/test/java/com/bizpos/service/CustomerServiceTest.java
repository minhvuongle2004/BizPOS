package com.bizpos.service;

import com.bizpos.dto.CustomerRequest;
import com.bizpos.entity.Customer;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.CustomerRepository;
import com.bizpos.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests cho CustomerService")
public class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleCustomer = Customer.builder()
                .id(1L)
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .email("nguyenvana@gmail.com")
                .address("123 Lê Lợi, TP.HCM")
                .build();
    }

    // =========================================================================
    // 1. TẠO KHÁCH HÀNG (CREATE CUSTOMER)
    // =========================================================================

    @Test
    @DisplayName("Tạo khách hàng thành công khi dữ liệu hợp lệ")
    void createCustomer_shouldSucceed_whenDataIsValid() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("  Trần Thị B  ")
                .phone("  0912345678  ")
                .email("  tranthib@gmail.com  ")
                .address("  456 Nguyễn Huệ  ")
                .build();

        when(customerRepository.existsByPhone("0912345678")).thenReturn(false);
        when(customerRepository.existsByEmail("tranthib@gmail.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        Customer created = customerService.createCustomer(request);

        assertNotNull(created);
        assertEquals("Trần Thị B", created.getFullName());
        assertEquals("0912345678", created.getPhone());
        assertEquals("tranthib@gmail.com", created.getEmail());
        assertEquals("456 Nguyễn Huệ", created.getAddress());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("Tạo khách hàng cho phép phone và email để trống (khách không cung cấp thông tin liên lạc)")
    void createCustomer_shouldAllowNullPhoneAndEmail() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Khách Không Tên Số")
                .phone(null)
                .email(null)
                .address(null)
                .build();

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer created = customerService.createCustomer(request);

        assertNotNull(created);
        assertEquals("Khách Không Tên Số", created.getFullName());
        assertNull(created.getPhone());
        assertNull(created.getEmail());
        verify(customerRepository, never()).existsByPhone(anyString());
        verify(customerRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Tạo khách hàng thất bại khi số điện thoại đã tồn tại -> ném DuplicateResourceException (409)")
    void createCustomer_shouldThrowDuplicateResourceException_whenPhoneAlreadyExists() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Khách Trùng SĐT")
                .phone("0901234567")
                .build();

        when(customerRepository.existsByPhone("0901234567")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(
                DuplicateResourceException.class,
                () -> customerService.createCustomer(request)
        );

        assertTrue(ex.getMessage().contains("0901234567"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Tạo khách hàng thất bại khi email đã tồn tại -> ném DuplicateResourceException (409)")
    void createCustomer_shouldThrowDuplicateResourceException_whenEmailAlreadyExists() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Khách Trùng Email")
                .phone("0988888888")
                .email("nguyenvana@gmail.com")
                .build();

        when(customerRepository.existsByPhone("0988888888")).thenReturn(false);
        when(customerRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(
                DuplicateResourceException.class,
                () -> customerService.createCustomer(request)
        );

        assertTrue(ex.getMessage().contains("nguyenvana@gmail.com"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Tạo khách hàng thất bại khi họ và tên rỗng hoặc null -> ném IllegalArgumentException")
    void createCustomer_shouldThrowIllegalArgumentException_whenFullNameIsEmptyOrNull() {
        CustomerRequest nullName = CustomerRequest.builder().fullName(null).build();
        CustomerRequest blankName = CustomerRequest.builder().fullName("   ").build();

        assertThrows(IllegalArgumentException.class, () -> customerService.createCustomer(nullName));
        assertThrows(IllegalArgumentException.class, () -> customerService.createCustomer(blankName));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Tạo khách hàng thất bại khi email sai định dạng regex -> ném IllegalArgumentException")
    void createCustomer_shouldThrowIllegalArgumentException_whenEmailFormatIsInvalid() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Lê Văn C")
                .email("email-khong-hop-le-chua-co-a-cong")
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> customerService.createCustomer(request)
        );

        assertTrue(ex.getMessage().contains("không đúng định dạng"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    // =========================================================================
    // 2. CẬP NHẬT KHÁCH HÀNG (UPDATE CUSTOMER)
    // =========================================================================

    @Test
    @DisplayName("Cập nhật khách hàng thành công khi dữ liệu hợp lệ")
    void updateCustomer_shouldSucceed_whenDataIsValid() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Nguyễn Văn A (Cập nhật)")
                .phone("0909999999")
                .email("vana_new@gmail.com")
                .address("Địa chỉ mới")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.existsByPhoneAndIdNot("0909999999", 1L)).thenReturn(false);
        when(customerRepository.existsByEmailAndIdNot("vana_new@gmail.com", 1L)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer updated = customerService.updateCustomer(1L, request);

        assertEquals("Nguyễn Văn A (Cập nhật)", updated.getFullName());
        assertEquals("0909999999", updated.getPhone());
        assertEquals("vana_new@gmail.com", updated.getEmail());
        assertEquals("Địa chỉ mới", updated.getAddress());
        verify(customerRepository).save(sampleCustomer);
    }

    @Test
    @DisplayName("Cập nhật khách hàng giữ nguyên SĐT và email của chính mình thì không bị báo trùng")
    void updateCustomer_shouldAllowKeepingSamePhoneAndEmail() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Nguyễn Văn A (Đổi tên thôi)")
                .phone("0901234567") // Giữ nguyên
                .email("nguyenvana@gmail.com") // Giữ nguyên
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.existsByPhoneAndIdNot("0901234567", 1L)).thenReturn(false);
        when(customerRepository.existsByEmailAndIdNot("nguyenvana@gmail.com", 1L)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer updated = customerService.updateCustomer(1L, request);

        assertEquals("Nguyễn Văn A (Đổi tên thôi)", updated.getFullName());
        assertEquals("0901234567", updated.getPhone());
        assertEquals("nguyenvana@gmail.com", updated.getEmail());
    }

    @Test
    @DisplayName("Cập nhật khách hàng thất bại khi SĐT bị trùng với khách hàng khác -> ném DuplicateResourceException (409)")
    void updateCustomer_shouldThrowDuplicateResourceException_whenPhoneAlreadyUsedByAnotherCustomer() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone("0911111111")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.existsByPhoneAndIdNot("0911111111", 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> customerService.updateCustomer(1L, request));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Cập nhật khách hàng thất bại khi Email bị trùng với khách hàng khác -> ném DuplicateResourceException (409)")
    void updateCustomer_shouldThrowDuplicateResourceException_whenEmailAlreadyUsedByAnotherCustomer() {
        CustomerRequest request = CustomerRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .email("trungemail@gmail.com")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));
        when(customerRepository.existsByPhoneAndIdNot("0901234567", 1L)).thenReturn(false);
        when(customerRepository.existsByEmailAndIdNot("trungemail@gmail.com", 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> customerService.updateCustomer(1L, request));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Cập nhật khách hàng thất bại khi không tìm thấy khách hàng -> ném ResourceNotFoundException (404)")
    void updateCustomer_shouldThrowResourceNotFoundException_whenCustomerDoesNotExist() {
        CustomerRequest request = CustomerRequest.builder().fullName("Bất kỳ").build();
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.updateCustomer(999L, request));
    }

    @Test
    @DisplayName("Cập nhật khách hàng thất bại khi họ và tên rỗng -> ném IllegalArgumentException")
    void updateCustomer_shouldThrowIllegalArgumentException_whenFullNameIsEmptyOrNull() {
        CustomerRequest request = CustomerRequest.builder().fullName("   ").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));

        assertThrows(IllegalArgumentException.class, () -> customerService.updateCustomer(1L, request));
    }

    // =========================================================================
    // 3. XÓA KHÁCH HÀNG (DELETE CUSTOMER)
    // =========================================================================

    @Test
    @DisplayName("Xóa khách hàng thành công khi khách hàng tồn tại")
    void deleteCustomer_shouldSucceed_whenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));

        customerService.deleteCustomer(1L);

        verify(customerRepository).delete(sampleCustomer);
    }

    @Test
    @DisplayName("Xóa khách hàng thất bại khi không tìm thấy -> ném ResourceNotFoundException (404)")
    void deleteCustomer_shouldThrowResourceNotFoundException_whenCustomerDoesNotExist() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.deleteCustomer(999L));
        verify(customerRepository, never()).delete(any(Customer.class));
    }
}
