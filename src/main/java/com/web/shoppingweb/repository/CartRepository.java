package com.web.shoppingweb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.Cart;
import com.web.shoppingweb.entity.CartStatus;
import com.web.shoppingweb.entity.User;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserAndStatus(User user, CartStatus status);
}
