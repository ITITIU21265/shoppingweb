package com.web.shoppingweb.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.cart.CartAddRequestDTO;
import com.web.shoppingweb.dto.cart.CartQuantityUpdateDTO;
import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.CartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final CartService cartService;

    public CartApiController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartSummaryDTO> getCart(Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(cartService.getActiveCart(username));
    }

    @PostMapping("/items")
    public ResponseEntity<CartSummaryDTO> addProduct(@Valid @RequestBody CartAddRequestDTO request,
                                                     Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(cartService.addProduct(username, request.getProductId(), request.getQuantity()));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartSummaryDTO> updateQuantity(@PathVariable Long variantId,
                                                         @Valid @RequestBody CartQuantityUpdateDTO request,
                                                         Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(cartService.updateQuantity(username, variantId, request.getQuantity()));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartSummaryDTO> removeItem(@PathVariable Long variantId,
                                                     Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(cartService.removeItem(username, variantId));
    }
}
