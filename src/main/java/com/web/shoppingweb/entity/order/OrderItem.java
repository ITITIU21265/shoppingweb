package com.web.shoppingweb.entity.order;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_seller_id", nullable = false)
    private OrderSeller orderSeller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "product_title", nullable = false, length = 255)
    private String productTitle;

    @Column(name = "product_name", nullable = false, length = 140)
    private String productName;

    @Column(name = "product_slug", nullable = false, length = 160)
    private String productSlug;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_amount", nullable = false)
    private Long unitPriceAmount;

    @Column(name = "line_total_amount", nullable = false)
    private Long lineTotalAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public OrderSeller getOrderSeller() {
        return orderSeller;
    }

    public void setOrderSeller(OrderSeller orderSeller) {
        this.orderSeller = orderSeller;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
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

    public String getProductImageUrl() {
        return productImageUrl;
    }

    public void setProductImageUrl(String productImageUrl) {
        this.productImageUrl = productImageUrl;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getUnitPriceAmount() {
        return unitPriceAmount;
    }

    public void setUnitPriceAmount(Long unitPriceAmount) {
        this.unitPriceAmount = unitPriceAmount;
    }

    public Long getLineTotalAmount() {
        return lineTotalAmount;
    }

    public void setLineTotalAmount(Long lineTotalAmount) {
        this.lineTotalAmount = lineTotalAmount;
    }
}
