package com.web.shoppingweb.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.order.OrderItem;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.supplier.SupplierRepository;

@Service
public class AdminDashboardService {

    private static final int DASHBOARD_PRODUCT_LIMIT = 8;
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final UserService userService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SupplierRepository supplierRepository;

    public AdminDashboardService(UserService userService,
                                 ProductService productService,
                                 ProductRepository productRepository,
                                 ProductVariantRepository productVariantRepository,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 SupplierRepository supplierRepository) {
        this.userService = userService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardData loadDashboard(Long selectedOrderId) {
        List<UserResponseDTO> users = userService.getAllUsers().stream()
                .sorted(Comparator.comparing(
                        UserResponseDTO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        Map<Long, Supplier> latestSupplierByUserId = buildLatestSupplierByUserId();
        Map<Long, Supplier> pendingSupplierByUserId =
                filterSuppliersByStatus(latestSupplierByUserId, SupplierStatus.PENDING);
        Map<Long, Supplier> approvedSupplierByUserId =
                filterSuppliersByStatus(latestSupplierByUserId, SupplierStatus.APPROVED);

        List<Order> allOrders = orderRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Order::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        List<Product> allProducts = productRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Product::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
        List<Product> latestProducts = allProducts.stream()
                .limit(DASHBOARD_PRODUCT_LIMIT)
                .toList();
        List<ProductVariant> lowStockVariants =
                productVariantRepository.findTop6ByStockQtyLessThanEqualAndStatusOrderByStockQtyAscIdAsc(
                        LOW_STOCK_THRESHOLD,
                        ProductVariantStatus.ACTIVE
                );
        List<OrderItem> allOrderItems = orderItemRepository.findAll();
        long lowStockCount = productVariantRepository.countByStockQtyLessThanEqualAndStatus(
                LOW_STOCK_THRESHOLD,
                ProductVariantStatus.ACTIVE
        );
        int categoryCount = productService.getAvailableCategories().size();

        Order selectedOrder = resolveSelectedOrder(allOrders, selectedOrderId);
        List<OrderItem> selectedOrderItems = selectedOrder == null
                ? Collections.emptyList()
                : orderItemRepository.findByOrderOrderByIdAsc(selectedOrder);

        return new AdminDashboardData(
                users,
                latestSupplierByUserId,
                pendingSupplierByUserId,
                approvedSupplierByUserId,
                allOrders,
                allProducts,
                latestProducts,
                lowStockVariants,
                allOrderItems,
                lowStockCount,
                categoryCount,
                selectedOrder,
                selectedOrderItems
        );
    }

    private Map<Long, Supplier> buildLatestSupplierByUserId() {
        Map<Long, Supplier> suppliersByUserId = new LinkedHashMap<>();
        supplierRepository.findAll().stream()
                .filter(supplier -> supplier.getSeller() != null && supplier.getSeller().getId() != null)
                .sorted(Comparator.comparing(
                        Supplier::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .forEach(supplier -> suppliersByUserId.putIfAbsent(supplier.getSeller().getId(), supplier));
        return suppliersByUserId;
    }

    private Map<Long, Supplier> filterSuppliersByStatus(Map<Long, Supplier> suppliersByUserId, SupplierStatus status) {
        Map<Long, Supplier> filteredSuppliers = new LinkedHashMap<>();
        suppliersByUserId.forEach((userId, supplier) -> {
            if (supplier.getStatus() == status) {
                filteredSuppliers.put(userId, supplier);
            }
        });
        return filteredSuppliers;
    }

    private Order resolveSelectedOrder(List<Order> allOrders, Long orderId) {
        if (orderId == null) {
            return null;
        }

        return allOrders.stream()
                .filter(order -> Objects.equals(order.getId(), orderId))
                .findFirst()
                .orElse(null);
    }

    public record AdminDashboardData(
            List<UserResponseDTO> users,
            Map<Long, Supplier> latestSupplierByUserId,
            Map<Long, Supplier> pendingSupplierByUserId,
            Map<Long, Supplier> approvedSupplierByUserId,
            List<Order> allOrders,
            List<Product> allProducts,
            List<Product> latestProducts,
            List<ProductVariant> lowStockVariants,
            List<OrderItem> allOrderItems,
            long lowStockCount,
            int categoryCount,
            Order selectedOrder,
            List<OrderItem> selectedOrderItems
    ) {
    }
}
