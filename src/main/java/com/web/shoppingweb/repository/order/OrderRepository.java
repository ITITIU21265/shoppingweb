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
            select o
            from Order o
            where o.seller.username = :username
            order by o.createdAt desc
            """)
    List<Order> findRecentBySellerUsername(@Param("username") String username, Pageable pageable);

    @Query("""
            select count(o.id)
            from Order o
            where o.seller.username = :username
            """)
    long countDistinctBySellerUsername(@Param("username") String username);

    @Query("""
            select count(distinct o.user.id)
            from Order o
            where o.seller.username = :username
            """)
    long countDistinctCustomersBySellerUsername(@Param("username") String username);

    boolean existsByOrderNumber(String orderNumber);
}
