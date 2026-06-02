package com.web.shoppingweb.repository.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.order.OrderSeller;

@Repository
public interface OrderSellerRepository extends JpaRepository<OrderSeller, Long> {
}
