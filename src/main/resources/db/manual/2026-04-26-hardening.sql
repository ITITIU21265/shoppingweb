-- Apply manually before running with spring.jpa.hibernate.ddl-auto=none.
-- This migration supports seller-owned sub-orders and keeps key lookups indexed.

ALTER TABLE orders
    ADD COLUMN seller_id BIGINT NULL AFTER cart_id;

UPDATE orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
SET o.seller_id = p.seller_id
WHERE o.seller_id IS NULL;

ALTER TABLE orders
    MODIFY seller_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_orders_seller
        FOREIGN KEY (seller_id) REFERENCES users(id);

CREATE INDEX idx_orders_seller_created_at ON orders(seller_id, created_at);
CREATE INDEX idx_products_seller_id ON products(seller_id);
CREATE INDEX idx_product_variants_stock_status ON product_variants(stock_qty, status);
