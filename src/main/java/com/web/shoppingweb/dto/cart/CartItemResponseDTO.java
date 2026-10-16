package com.web.shoppingweb.dto.cart;

import java.math.BigDecimal;

public class CartItemResponseDTO {

    private Long variantId;
    private Long productId;
    private String productName;
    private String productSlug;
    private String imageUrl;
    private String sellerName;
    private String sku;
    private String sizeLabel;
    private String colorName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public CartItemResponseDTO() {
    }

    public CartItemResponseDTO(Long variantId,
                               Long productId,
                               String productName,
                               String productSlug,
                               String imageUrl,
                               String sellerName,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal lineTotal) {
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.productSlug = productSlug;
        this.imageUrl = imageUrl;
        this.sellerName = sellerName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public CartItemResponseDTO(Long variantId,
                               Long productId,
                               String productName,
                               String productSlug,
                               String imageUrl,
                               String sellerName,
                               String sku,
                               String sizeLabel,
                               String colorName,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal lineTotal) {
        this(variantId, productId, productName, productSlug, imageUrl, sellerName,
                quantity, unitPrice, lineTotal);
        this.sku = sku;
        this.sizeLabel = sizeLabel;
        this.colorName = colorName;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public void setProductSlug(String productSlug) {
        this.productSlug = productSlug;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getSizeLabel() {
        return sizeLabel;
    }

    public void setSizeLabel(String sizeLabel) {
        this.sizeLabel = sizeLabel;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
