package com.web.shoppingweb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.Order;
import com.web.shoppingweb.entity.User;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    List<Order> findTop5ByOrderByCreatedAtDesc();

    Optional<Order> findByIdAndUser(Long id, User user);

    boolean existsByOrderNumber(String orderNumber);
}
