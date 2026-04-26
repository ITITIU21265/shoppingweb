package com.web.shoppingweb.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderDetailDTO {

    private Long id;
    private String orderNumber;
    private String status;
    private String statusLabel;
    private String createdAtLabel;
    private String customerName;
    private String customerEmail;
    private String shippingAddress;
    private long totalQuantity;
    private BigDecimal subtotal;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items = new ArrayList<>();

    public OrderDetailDTO() {
    }

    public OrderDetailDTO(Long id,
                          String orderNumber,
                          String status,
                          String statusLabel,
                          String createdAtLabel,
                          String customerName,
                          String customerEmail,
                          String shippingAddress,
                          long totalQuantity,
                          BigDecimal subtotal,
                          LocalDateTime createdAt,
                          List<OrderItemDTO> items) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.status = status;
        this.statusLabel = statusLabel;
        this.createdAtLabel = createdAtLabel;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.shippingAddress = shippingAddress;
        this.totalQuantity = totalQuantity;
        this.subtotal = subtotal;
        this.createdAt = createdAt;
        this.items = items;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getCreatedAtLabel() {
        return createdAtLabel;
    }

    public void setCreatedAtLabel(String createdAtLabel) {
        this.createdAtLabel = createdAtLabel;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }
}
