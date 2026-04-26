package com.web.shoppingweb.dto.cart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartSummaryDTO {

    private List<CartItemResponseDTO> items = new ArrayList<>();
    private long totalQuantity;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public CartSummaryDTO() {
    }

    public CartSummaryDTO(List<CartItemResponseDTO> items, long totalQuantity, BigDecimal subtotal) {
        this.items = items;
        this.totalQuantity = totalQuantity;
        this.subtotal = subtotal;
    }

    public List<CartItemResponseDTO> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponseDTO> items) {
        this.items = items;
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
}
