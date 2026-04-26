package com.web.shoppingweb.controller.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;

@Component
public class SellerDashboardModelBuilder {

    private static final int DASHBOARD_PRODUCT_LIMIT = 8;
    private static final int DASHBOARD_ORDER_LIMIT = 12;
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public SellerDashboardModelBuilder(ProductRepository productRepository,
                                       ProductVariantRepository productVariantRepository,
                                       OrderRepository orderRepository,
                                       OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public void populate(Model model, String username) {
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

        model.addAttribute("sellerManagedProducts", productRepository.countBySeller_Username(username));
        model.addAttribute("sellerFeaturedProducts", productRepository.countBySeller_UsernameAndFeaturedTrue(username));
        model.addAttribute(
                "sellerLowStockCount",
                productVariantRepository.countByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatus(
                        username,
                        LOW_STOCK_THRESHOLD,
                        ProductVariantStatus.ACTIVE
                )
        );
        model.addAttribute("sellerCategoryCount", categoryCount);
        model.addAttribute("sellerActiveProducts", activeProductCount);
        model.addAttribute("sellerOrderCount", orderRepository.countDistinctBySellerUsername(username));
        model.addAttribute("sellerCustomerCount", orderRepository.countDistinctCustomersBySellerUsername(username));
        model.addAttribute("sellerRevenue", BigDecimal.valueOf(sellerRevenueAmount == null ? 0L : sellerRevenueAmount, 2));
        model.addAttribute("dashboardOrders", orderRepository.findRecentBySellerUsername(username, PageRequest.of(0, DASHBOARD_ORDER_LIMIT)));
        model.addAttribute("dashboardProducts", sellerProducts.stream().limit(DASHBOARD_PRODUCT_LIMIT).toList());
        model.addAttribute("dashboardLowStockVariants", lowStockVariants);
        model.addAttribute("dashboardCoupons", MarketingDashboardFixtures.buildMarketingCoupons());
    }
}
