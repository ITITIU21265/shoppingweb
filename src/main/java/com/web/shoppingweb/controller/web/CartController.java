package com.web.shoppingweb.controller.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.cart.CartItemResponseDTO;
import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.SavedService;

@Controller
@RequestMapping("/cart")
@PreAuthorize("isAuthenticated()")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;
    private final SavedService savedService;

    public CartController(CartService cartService, SavedService savedService) {
        this.cartService = cartService;
        this.savedService = savedService;
    }

    @GetMapping
    public String cart(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        CartSummaryDTO cartSummary = cartService.getActiveCart(username);
        Map<String, List<CartItemResponseDTO>> cartItemsBySeller = cartSummary.getItems().stream()
                .collect(Collectors.groupingBy(
                        this::resolveSellerName,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        model.addAttribute("cartSummary", cartSummary);
        model.addAttribute("cartItemsBySeller", cartItemsBySeller);
        model.addAttribute("savedProducts", savedService.getSavedProducts(username));
        return "cart";
    }

    private String resolveSellerName(CartItemResponseDTO item) {
        String sellerName = item.getSellerName();
        return sellerName == null || sellerName.isBlank() ? "Unknown Seller" : sellerName;
    }

    @PostMapping(value = "/items/{productId}", produces = MediaType.TEXT_HTML_VALUE)
    public String addProduct(@PathVariable Long productId,
                             @RequestParam(defaultValue = "1") int quantity,
                             @RequestParam(required = false) Long variantId,
                             @RequestParam(defaultValue = "/cart") String redirectTo,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        addProductOrVariant(username, productId, variantId, quantity);
        redirectAttributes.addFlashAttribute("successMessage", "Product added to cart.");
        return "redirect:" + redirectTo;
    }

    @PostMapping(value = "/items/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> addProductJson(@PathVariable Long productId,
                                            @RequestParam(defaultValue = "1") int quantity,
                                            @RequestParam(required = false) Long variantId,
                                            Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        try {
            return ResponseEntity.ok(addProductOrVariant(username, productId, variantId, quantity));
        } catch (ResourceNotFoundException ex) {
            return jsonError(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return jsonError(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to add product {} variant {} to cart for user {}",
                    productId, variantId, username, ex);
            return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to update the cart.");
        }
    }

    @PostMapping("/items/{variantId}/quantity")
    public String updateQuantity(@PathVariable Long variantId,
                                 @RequestParam int quantity,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        cartService.updateQuantity(username, variantId, quantity);
        redirectAttributes.addFlashAttribute("successMessage", "Cart updated successfully.");
        return "redirect:/cart";
    }

    @PostMapping("/items/{variantId}/remove")
    public String removeItem(@PathVariable Long variantId,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        cartService.removeItem(username, variantId);
        redirectAttributes.addFlashAttribute("successMessage", "Item removed from cart.");
        return "redirect:/cart";
    }

    private CartSummaryDTO addProductOrVariant(String username,
                                               Long productId,
                                               Long variantId,
                                               int quantity) {
        return variantId == null
                ? cartService.addProduct(username, productId, quantity)
                : cartService.addVariant(username, productId, variantId, quantity);
    }

    private ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
