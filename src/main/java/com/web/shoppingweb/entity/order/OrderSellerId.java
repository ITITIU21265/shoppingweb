package com.web.shoppingweb.entity.order;

import java.io.Serializable;
import java.util.Objects;

public class OrderSellerId implements Serializable {

    private Long order;
    private Long seller;

    public OrderSellerId() {
    }

    public OrderSellerId(Long order, Long seller) {
        this.order = order;
        this.seller = seller;
    }

    public Long getOrder() {
        return order;
    }

    public void setOrder(Long order) {
        this.order = order;
    }

    public Long getSeller() {
        return seller;
    }

    public void setSeller(Long seller) {
        this.seller = seller;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderSellerId that = (OrderSellerId) o;
        return Objects.equals(order, that.order) && Objects.equals(seller, that.seller);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, seller);
    }
}
