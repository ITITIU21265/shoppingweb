package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.OrderService;

@Controller
@RequestMapping("/orders")
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String orders(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        model.addAttribute("orders", orderService.getOrders(username));
        return "orders";
    }

    @GetMapping("/{orderId}")
    public String orderDetail(@PathVariable Long orderId,
                              Authentication authentication,
                              Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        model.addAttribute("order", orderService.getOrderDetail(username, orderId));
        return "order-detail";
    }
}
