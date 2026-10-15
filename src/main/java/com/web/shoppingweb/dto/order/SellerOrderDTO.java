package com.web.shoppingweb.dto.order;

import java.time.LocalDateTime;

import com.web.shoppingweb.entity.order.OrderSellerStatus;

public class SellerOrderDTO {

    private Long id;
    private Long masterOrderId;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private int itemsCount;
    private Long subtotalAmount;
    private OrderSellerStatus status;
    private LocalDateTime createdAt;

    public SellerOrderDTO(Long id,
                          Long masterOrderId,
                          String orderNumber,
                          String customerName,
                          String customerEmail,
                          int itemsCount,
                          Long subtotalAmount,
                          OrderSellerStatus status,
                          LocalDateTime createdAt) {
        this.id = id;
        this.masterOrderId = masterOrderId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.itemsCount = itemsCount;
        this.subtotalAmount = subtotalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMasterOrderId() {
        return masterOrderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public int getItemsCount() {
        return itemsCount;
    }

    public int getTotalQuantity() {
        return itemsCount;
    }

    public Long getSubtotalAmount() {
        return subtotalAmount;
    }

    public OrderSellerStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
