package com.bizpos.repository;

import com.bizpos.entity.OrderExchangeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderExchangeItemRepository extends JpaRepository<OrderExchangeItem, Long> {
    List<OrderExchangeItem> findByOrderReturnId(Long orderReturnId);
}
