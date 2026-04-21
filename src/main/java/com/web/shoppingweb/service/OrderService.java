package com.web.shoppingweb.service;

import java.util.List;

import com.web.shoppingweb.dto.CheckoutRequestDTO;
import com.web.shoppingweb.dto.OrderDetailDTO;
import com.web.shoppingweb.dto.OrderSummaryDTO;

public interface OrderService {

    OrderDetailDTO checkout(String username, CheckoutRequestDTO request);

    List<OrderSummaryDTO> getOrders(String username);

    OrderDetailDTO getOrderDetail(String username, Long orderId);
}
