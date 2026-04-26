package com.web.shoppingweb.repository.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.order.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderOrderByIdAsc(Order order);

    @Query("""
            select coalesce(sum(oi.lineTotalAmount), 0)
            from OrderItem oi
            join oi.product p
            where p.seller.username = :username
            """)
    Long sumLineTotalAmountBySellerUsername(@Param("username") String username);
}
