package com.web.shoppingweb.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.entity.order.OrderItem;
import com.web.shoppingweb.entity.order.OrderSeller;
import com.web.shoppingweb.dto.order.SellerOrderDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderSellerRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;

@Service
public class SellerDashboardService {

    private static final int DASHBOARD_PRODUCT_LIMIT = 8;
    private static final int DASHBOARD_ORDER_LIMIT = 12;
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderSellerRepository orderSellerRepository;

    public SellerDashboardService(ProductRepository productRepository,
                                  ProductVariantRepository productVariantRepository,
                                  OrderItemRepository orderItemRepository,
                                  OrderSellerRepository orderSellerRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderSellerRepository = orderSellerRepository;
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
        Long sellerRevenueAmount = orderSellerRepository.sumTotalAmountBySellerUsername(username);

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
                orderSellerRepository.countBySeller_Username(username),
                orderSellerRepository.countDistinctCustomersBySellerUsername(username),
                BigDecimal.valueOf(sellerRevenueAmount == null ? 0L : sellerRevenueAmount),
                orderSellerRepository
                        .findBySeller_UsernameOrderByCreatedAtDesc(username, PageRequest.of(0, DASHBOARD_ORDER_LIMIT))
                        .stream()
                        .map(this::toSellerOrderDTO)
                        .toList(),
                sellerProducts.stream().limit(DASHBOARD_PRODUCT_LIMIT).toList(),
                lowStockVariants
        );
    }

    @Transactional(readOnly = true)
    public List<SellerOrderDTO> getSellerSubOrders(String username) {
        return orderSellerRepository.findBySeller_UsernameOrderByCreatedAtDesc(username).stream()
                .map(this::toSellerOrderDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderSeller getSellerSubOrder(String username, Long orderSellerId) {
        return orderSellerRepository.findByIdAndSeller_Username(orderSellerId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Seller order not found"));
    }

    @Transactional(readOnly = true)
    public List<OrderItem> getSellerSubOrderItems(String username, Long orderSellerId) {
        getSellerSubOrder(username, orderSellerId);
        return orderItemRepository.findByOrderSeller_IdAndOrderSeller_Seller_UsernameOrderByIdAsc(
                orderSellerId,
                username
        );
    }

    private SellerOrderDTO toSellerOrderDTO(OrderSeller orderSeller) {
        List<OrderItem> sellerItems = orderItemRepository
                .findByOrderSeller_IdAndOrderSeller_Seller_UsernameOrderByIdAsc(
                        orderSeller.getId(),
                        orderSeller.getSeller().getUsername()
                );
        int itemsCount = sellerItems.stream()
                .map(OrderItem::getQuantity)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();

        return new SellerOrderDTO(
                orderSeller.getId(),
                orderSeller.getOrder().getId(),
                orderSeller.getOrder().getOrderNumber(),
                orderSeller.getOrder().getCustomerName(),
                orderSeller.getOrder().getCustomerEmail(),
                itemsCount,
                orderSeller.getSubtotalAmount(),
                orderSeller.getStatus(),
                orderSeller.getCreatedAt()
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
            List<SellerOrderDTO> recentOrders,
            List<Product> recentProducts,
            List<ProductVariant> lowStockVariants
    ) {
    }
}
