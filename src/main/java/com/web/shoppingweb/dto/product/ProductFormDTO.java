package com.web.shoppingweb.dto.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ProductFormDTO {

    @NotBlank(message = "Product name is required")
    @Size(max = 140, message = "Product name must not exceed 140 characters")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "1", message = "Price must be at least 1 VND")
    @Digits(integer = 10, fraction = 0, message = "Price must be a whole VND amount with at most 10 digits")
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Image URL is required")
    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;

    @Valid
    @Size(max = 100, message = "A product cannot have more than 100 variants")
    private List<VariantDTO> variants = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public List<VariantDTO> getVariants() {
        return variants;
    }

    public void setVariants(List<VariantDTO> variants) {
        this.variants = variants;
    }

    public static class VariantDTO {

        @Positive(message = "Variant ID must be positive")
        private Long id;

        @Positive(message = "Size ID must be positive")
        private Long sizeId;

        @Positive(message = "Color ID must be positive")
        private Long colorId;

        @NotNull(message = "Variant price is required")
        @DecimalMin(value = "1", message = "Variant price must be at least 1 VND")
        @Digits(integer = 10, fraction = 0,
                message = "Variant price must be a whole VND amount with at most 10 digits")
        private BigDecimal price;

        @NotNull(message = "Variant stock quantity is required")
        @Min(value = 0, message = "Variant stock quantity cannot be negative")
        private Integer stockQty;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getSizeId() {
            return sizeId;
        }

        public void setSizeId(Long sizeId) {
            this.sizeId = sizeId;
        }

        public Long getColorId() {
            return colorId;
        }

        public void setColorId(Long colorId) {
            this.colorId = colorId;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getStockQty() {
            return stockQty;
        }

        public void setStockQty(Integer stockQty) {
            this.stockQty = stockQty;
        }
    }
}
