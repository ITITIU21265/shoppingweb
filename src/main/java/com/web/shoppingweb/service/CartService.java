package com.web.shoppingweb.service;

import com.web.shoppingweb.dto.CartSummaryDTO;

public interface CartService {

    CartSummaryDTO getActiveCart(String username);

    CartSummaryDTO addProduct(String username, Long productId, int quantity);

    CartSummaryDTO updateQuantity(String username, Long variantId, int quantity);

    CartSummaryDTO removeItem(String username, Long variantId);

    long countCartItems(String username);
}
