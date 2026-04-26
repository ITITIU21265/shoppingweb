package com.web.shoppingweb.dto.order;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CheckoutRequestDTO {

    @NotEmpty(message = "Select at least one item to checkout")
    private List<Long> selectedVariantIds = new ArrayList<>();

    @NotBlank(message = "Shipping address is required")
    @Size(max = 1000, message = "Shipping address must be at most 1000 characters")
    private String shippingAddress;

    public List<Long> getSelectedVariantIds() {
        return selectedVariantIds;
    }

    public void setSelectedVariantIds(List<Long> selectedVariantIds) {
        this.selectedVariantIds = selectedVariantIds;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
