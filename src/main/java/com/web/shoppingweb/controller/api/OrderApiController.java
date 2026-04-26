package com.web.shoppingweb.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.order.CheckoutRequestDTO;
import com.web.shoppingweb.dto.order.OrderDetailDTO;
import com.web.shoppingweb.dto.order.OrderSummaryDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryDTO>> getOrders(Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(orderService.getOrders(username));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailDTO> getOrderDetail(@PathVariable Long orderId,
                                                         Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.ok(orderService.getOrderDetail(username, orderId));
    }

    @PostMapping
    public ResponseEntity<OrderDetailDTO> checkout(@Valid @RequestBody CheckoutRequestDTO request,
                                                   Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkout(username, request));
    }
}
