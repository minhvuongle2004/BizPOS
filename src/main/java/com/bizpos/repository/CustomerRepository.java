package com.bizpos.repository;

import com.bizpos.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Customer> findByFullNameContainingIgnoreCaseOrPhoneContaining(String fullName, String phone);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " c.phone LIKE CONCAT('%', :keyword, '%'))")
    org.springframework.data.domain.Page<Customer> searchCustomers(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
}
