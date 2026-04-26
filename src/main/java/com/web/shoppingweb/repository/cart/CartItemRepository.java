package com.web.shoppingweb.repository.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartItem;
import com.web.shoppingweb.entity.cart.CartItemId;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByCartOrderByCreatedAtAsc(Cart cart);

    Optional<CartItem> findByCartAndVariant_Id(Cart cart, Long variantId);

    @Query("select coalesce(sum(ci.quantity), 0) from CartItem ci where ci.cart = :cart")
    long sumQuantityByCart(@Param("cart") Cart cart);
}
