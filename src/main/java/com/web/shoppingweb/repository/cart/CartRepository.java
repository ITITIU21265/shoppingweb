package com.web.shoppingweb.repository.cart;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartStatus;
import com.web.shoppingweb.entity.user.User;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserAndStatus(User user, CartStatus status);
}
