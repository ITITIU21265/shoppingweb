package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.SavedService;

@Controller
@RequestMapping("/cart")
@PreAuthorize("isAuthenticated()")
public class CartController {

    private final CartService cartService;
    private final SavedService savedService;

    public CartController(CartService cartService, SavedService savedService) {
        this.cartService = cartService;
        this.savedService = savedService;
    }

    @GetMapping
    public String cart(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        model.addAttribute("cartSummary", cartService.getActiveCart(username));
        model.addAttribute("savedProducts", savedService.getSavedProducts(username));
        return "cart";
    }

    @PostMapping("/items/{productId}")
    public String addProduct(@PathVariable Long productId,
                             @RequestParam(defaultValue = "1") int quantity,
                             @RequestParam(defaultValue = "/cart") String redirectTo,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        cartService.addProduct(username, productId, quantity);
        redirectAttributes.addFlashAttribute("successMessage", "Product added to cart.");
        return "redirect:" + redirectTo;
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
}
