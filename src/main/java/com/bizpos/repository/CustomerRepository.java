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

    boolean existsByEmail(String email);

    List<Customer> findByFullNameContainingIgnoreCaseOrPhoneContaining(String fullName, String phone);
}
