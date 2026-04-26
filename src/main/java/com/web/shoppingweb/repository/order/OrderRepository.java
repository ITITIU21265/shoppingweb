package com.web.shoppingweb.repository.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.user.User;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    List<Order> findTop5ByOrderByCreatedAtDesc();

    List<Order> findTop10ByOrderByCreatedAtDesc();

    Optional<Order> findByIdAndUser(Long id, User user);

    @Query("""
            select distinct oi.order
            from OrderItem oi
            where oi.product.seller.username = :username
            order by oi.order.createdAt desc
            """)
    List<Order> findRecentBySellerUsername(@Param("username") String username, Pageable pageable);

    @Query("""
            select count(distinct oi.order.id)
            from OrderItem oi
            where oi.product.seller.username = :username
            """)
    long countDistinctBySellerUsername(@Param("username") String username);

    @Query("""
            select count(distinct oi.order.user.id)
            from OrderItem oi
            where oi.product.seller.username = :username
            """)
    long countDistinctCustomersBySellerUsername(@Param("username") String username);

    boolean existsByOrderNumber(String orderNumber);
}
