package com.web.shoppingweb.controller.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import com.web.shoppingweb.dto.order.OrderSummaryDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.OrderService;
import com.web.shoppingweb.service.SavedService;

@Component
public class CustomerDashboardModelBuilder {

    private static final int CUSTOMER_SAVED_PREVIEW_LIMIT = 4;
    private static final int CUSTOMER_ORDER_PREVIEW_LIMIT = 5;

    private final OrderService orderService;
    private final SavedService savedService;
    private final CartService cartService;

    public CustomerDashboardModelBuilder(OrderService orderService,
                                         SavedService savedService,
                                         CartService cartService) {
        this.orderService = orderService;
        this.savedService = savedService;
        this.cartService = cartService;
    }

    public void populate(Model model, String username) {
        List<OrderSummaryDTO> customerOrders = orderService.getOrders(username);
        List<Product> savedProducts = savedService.getSavedProducts(username);
        CartSummaryDTO cartSummary = cartService.getActiveCart(username);
        BigDecimal totalSpent = customerOrders.stream()
                .map(OrderSummaryDTO::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("customerOrders", customerOrders);
        model.addAttribute("customerSavedProducts", savedProducts);
        model.addAttribute("customerRecentOrders", customerOrders.stream().limit(CUSTOMER_ORDER_PREVIEW_LIMIT).toList());
        model.addAttribute("customerSavedPreview", savedProducts.stream().limit(CUSTOMER_SAVED_PREVIEW_LIMIT).toList());
        model.addAttribute("customerCartSummary", cartSummary);
        model.addAttribute("customerOrderCount", customerOrders.size());
        model.addAttribute("customerSavedCount", savedProducts.size());
        model.addAttribute("customerCartCount", cartSummary.getTotalQuantity());
        model.addAttribute("customerTotalSpent", totalSpent);
    }
}
