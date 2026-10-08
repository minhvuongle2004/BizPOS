package com.bizpos.repository;

import com.bizpos.entity.OrderReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, Long> {
    List<OrderReturnItem> findByOrderReturnId(Long orderReturnId);
}
