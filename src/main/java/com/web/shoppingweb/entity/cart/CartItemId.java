package com.web.shoppingweb.entity.cart;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CartItemId implements Serializable {

    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "variant_id")
    private Long variantId;

    public CartItemId() {
    }

    public CartItemId(Long cartId, Long variantId) {
        this.cartId = cartId;
        this.variantId = variantId;
    }

    public Long getCartId() {
        return cartId;
    }

    public void setCartId(Long cartId) {
        this.cartId = cartId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CartItemId that)) {
            return false;
        }
        return Objects.equals(cartId, that.cartId) && Objects.equals(variantId, that.variantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cartId, variantId);
    }
}
