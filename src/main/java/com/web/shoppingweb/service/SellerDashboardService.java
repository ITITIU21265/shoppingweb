package com.web.shoppingweb.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;

@Service
public class SellerDashboardService {

    private static final int DASHBOARD_PRODUCT_LIMIT = 8;
    private static final int DASHBOARD_ORDER_LIMIT = 12;
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public SellerDashboardService(ProductRepository productRepository,
                                  ProductVariantRepository productVariantRepository,
                                  OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public SellerDashboardData loadDashboard(String username) {
        List<Product> sellerProducts = productRepository.findBySeller_UsernameOrderByCreatedAtDesc(username);
        List<ProductVariant> lowStockVariants =
                productVariantRepository.findTop5ByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatusOrderByStockQtyAscIdAsc(
                        username,
                        LOW_STOCK_THRESHOLD,
                        ProductVariantStatus.ACTIVE
                );
        Long sellerRevenueAmount = orderItemRepository.sumLineTotalAmountBySellerUsername(username);

        long categoryCount = sellerProducts.stream()
                .map(Product::getCategory)
                .distinct()
                .count();
        long activeProductCount = sellerProducts.stream()
                .filter(Product::isActive)
                .count();

        return new SellerDashboardData(
                productRepository.countBySeller_Username(username),
                productRepository.countBySeller_UsernameAndFeaturedTrue(username),
                productVariantRepository.countByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatus(
                        username,
                        LOW_STOCK_THRESHOLD,
                        ProductVariantStatus.ACTIVE
                ),
                categoryCount,
                activeProductCount,
                orderRepository.countDistinctBySellerUsername(username),
                orderRepository.countDistinctCustomersBySellerUsername(username),
                BigDecimal.valueOf(sellerRevenueAmount == null ? 0L : sellerRevenueAmount, 2),
                orderRepository.findRecentBySellerUsername(username, PageRequest.of(0, DASHBOARD_ORDER_LIMIT)),
                sellerProducts.stream().limit(DASHBOARD_PRODUCT_LIMIT).toList(),
                lowStockVariants
        );
    }

    public record SellerDashboardData(
            long managedProducts,
            long featuredProducts,
            long lowStockCount,
            long categoryCount,
            long activeProducts,
            long orderCount,
            long customerCount,
            BigDecimal revenue,
            List<Order> recentOrders,
            List<Product> recentProducts,
            List<ProductVariant> lowStockVariants
    ) {
    }
}
