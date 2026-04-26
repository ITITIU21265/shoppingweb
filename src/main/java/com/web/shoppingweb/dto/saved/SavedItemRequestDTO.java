package com.web.shoppingweb.dto.saved;

import jakarta.validation.constraints.NotNull;

public class SavedItemRequestDTO {

    @NotNull(message = "Product id is required")
    private Long productId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
