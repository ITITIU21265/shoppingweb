package com.web.shoppingweb.dto;

import jakarta.validation.constraints.Min;

public class CartQuantityUpdateDTO {

    @Min(value = 0, message = "Quantity must not be negative")
    private Integer quantity = 1;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
