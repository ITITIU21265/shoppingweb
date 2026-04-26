package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import com.web.shoppingweb.dto.order.CheckoutRequestDTO;
import com.web.shoppingweb.dto.order.OrderDetailDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.OrderService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/checkout")
@PreAuthorize("isAuthenticated()")
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;

    public CheckoutController(CartService cartService, OrderService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @GetMapping
    public String checkout(@ModelAttribute("checkoutForm") CheckoutRequestDTO checkoutForm,
                           Authentication authentication,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);

        if (checkoutForm.getSelectedVariantIds() == null || checkoutForm.getSelectedVariantIds().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Select at least one item to checkout.");
            return "redirect:/cart";
        }

        try {
            model.addAttribute("cartSummary", cartService.getSelectedCart(username, checkoutForm.getSelectedVariantIds()));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cart";
        }

        return "checkout";
    }

    @PostMapping
    public String placeOrder(@Valid @ModelAttribute("checkoutForm") CheckoutRequestDTO checkoutForm,
                             BindingResult bindingResult,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        CartSummaryDTO cartSummary;
        try {
            cartSummary = cartService.getSelectedCart(username, checkoutForm.getSelectedVariantIds());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cart";
        }

        if (cartSummary.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your cart is empty.");
            return "redirect:/cart";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("cartSummary", cartSummary);
            model.addAttribute("errorMessage", "Please select items and provide a shipping address.");
            return "checkout";
        }

        try {
            OrderDetailDTO order = orderService.checkout(username, checkoutForm);
            return "redirect:/checkout/success/" + order.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("cartSummary", cartSummary);
            model.addAttribute("errorMessage", ex.getMessage());
            return "checkout";
        }
    }

    @GetMapping("/success/{orderId}")
    public String success(@PathVariable Long orderId,
                          Authentication authentication,
                          Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        model.addAttribute("order", orderService.getOrderDetail(username, orderId));
        return "checkout-success";
    }
}
