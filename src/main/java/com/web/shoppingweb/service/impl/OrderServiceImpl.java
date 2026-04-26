package com.web.shoppingweb.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.order.CheckoutRequestDTO;
import com.web.shoppingweb.dto.order.OrderDetailDTO;
import com.web.shoppingweb.dto.order.OrderItemDTO;
import com.web.shoppingweb.dto.order.OrderSummaryDTO;
import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartItem;
import com.web.shoppingweb.entity.cart.CartItemId;
import com.web.shoppingweb.entity.cart.CartStatus;
import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.order.OrderItem;
import com.web.shoppingweb.entity.order.OrderStatus;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.cart.CartItemRepository;
import com.web.shoppingweb.repository.cart.CartRepository;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;
import com.web.shoppingweb.service.OrderService;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ORDER_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            ProductVariantRepository productVariantRepository,
                            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
    }

    @Override
    public OrderDetailDTO checkout(String username, CheckoutRequestDTO request) {
        User user = getUser(username);
        Cart cart = cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Your cart is empty."));

        Set<Long> selectedIds = normalizeVariantIds(request.getSelectedVariantIds());
        List<CartItem> cartItems = cartItemRepository.findByCartOrderByCreatedAtAsc(cart);
        List<CartItem> selectedCartItems = resolveSelectedCartItems(cartItems, selectedIds);
        List<CartItem> remainingCartItems = cartItems.stream()
                .filter(cartItem -> !selectedIds.contains(cartItem.getVariant().getId()))
                .toList();

        Map<Long, ProductVariant> lockedVariants = lockSelectedVariants(selectedIds);
        String shippingAddress = normalizeAddress(request.getShippingAddress());
        Map<Long, CheckoutGroup> groupsBySeller = new LinkedHashMap<>();

        for (CartItem cartItem : selectedCartItems) {
            ProductVariant variant = lockedVariants.get(cartItem.getVariant().getId());
            if (variant == null) {
                throw new IllegalArgumentException("Some selected items are no longer available.");
            }
            Product product = variant.getProduct();
            validateCheckoutItem(product, variant, cartItem.getQuantity());

            groupsBySeller.computeIfAbsent(
                    product.getSeller().getId(),
                    sellerId -> new CheckoutGroup(product.getSeller())
            ).add(cartItem, variant);

            int remainingStock = variant.getStockQty() - cartItem.getQuantity();
            variant.setStockQty(remainingStock);
        }

        List<OrderDetailDTO> createdOrders = new ArrayList<>();
        for (CheckoutGroup group : groupsBySeller.values()) {
            createdOrders.add(createSellerOrder(user, cart, group, shippingAddress));
        }

        moveRemainingItemsToFreshCart(user, cart, cartItems, remainingCartItems);
        cart.setStatus(CartStatus.ORDERED);
        cartRepository.save(cart);

        return createdOrders.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDTO> getOrders(String username) {
        User user = getUser(username);
        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailDTO getOrderDetail(String username, Long orderId) {
        User user = getUser(username);
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        List<OrderItem> items = orderItemRepository.findByOrderOrderByIdAsc(order);
        return toDetail(order, items);
    }

    private void validateCheckoutItem(Product product, ProductVariant variant, int quantity) {
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product \"" + product.getName() + "\" is no longer available.");
        }
        if (variant.getStatus() != ProductVariantStatus.ACTIVE) {
            throw new IllegalArgumentException("Variant \"" + variant.getSku() + "\" is no longer available.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Cart contains an invalid quantity.");
        }
        if (variant.getStockQty() == null || variant.getStockQty() < quantity) {
            throw new IllegalArgumentException("Not enough stock for \"" + product.getName() + "\".");
        }
    }

    private Map<Long, ProductVariant> lockSelectedVariants(Set<Long> selectedIds) {
        List<Long> sortedIds = selectedIds.stream()
                .sorted()
                .toList();
        List<ProductVariant> variants = productVariantRepository.findAllByIdInForUpdate(sortedIds);
        if (variants.size() != selectedIds.size()) {
            throw new IllegalArgumentException("Some selected items are no longer available.");
        }

        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (ProductVariant variant : variants) {
            lockedVariants.put(variant.getId(), variant);
        }
        return lockedVariants;
    }

    private OrderDetailDTO createSellerOrder(User customer,
                                             Cart cart,
                                             CheckoutGroup group,
                                             String shippingAddress) {
        long subtotalAmount = 0L;
        int totalQuantity = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order();
        order.setUser(customer);
        order.setCart(cart);
        order.setSeller(group.seller());
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCustomerName(customer.getFullName());
        order.setCustomerEmail(customer.getEmail());
        order.setShippingAddress(shippingAddress);

        for (CheckoutLine line : group.lines()) {
            ProductVariant variant = line.variant();
            Product product = variant.getProduct();
            int quantity = line.cartItem().getQuantity();

            long unitPriceAmount = defaultAmount(variant.getPriceAmount());
            long lineTotalAmount = Math.multiplyExact(unitPriceAmount, quantity);
            subtotalAmount = Math.addExact(subtotalAmount, lineTotalAmount);
            totalQuantity = Math.addExact(totalQuantity, quantity);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setVariant(variant);
            orderItem.setProductName(product.getName());
            orderItem.setProductSlug(product.getSlug());
            orderItem.setProductImageUrl(product.getImageUrl());
            orderItem.setSku(variant.getSku());
            orderItem.setQuantity(quantity);
            orderItem.setUnitPriceAmount(unitPriceAmount);
            orderItem.setLineTotalAmount(lineTotalAmount);
            orderItems.add(orderItem);
        }

        order.setTotalQuantity(totalQuantity);
        order.setSubtotalAmount(subtotalAmount);
        Order savedOrder = orderRepository.save(order);
        orderItemRepository.saveAll(orderItems);
        return toDetail(savedOrder, orderItems);
    }

    private List<CartItem> resolveSelectedCartItems(List<CartItem> cartItems, Set<Long> selectedIds) {
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to checkout.");
        }

        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty.");
        }

        List<CartItem> selectedItems = cartItems.stream()
                .filter(cartItem -> selectedIds.contains(cartItem.getVariant().getId()))
                .toList();

        if (selectedItems.isEmpty() || selectedItems.size() != selectedIds.size()) {
            throw new IllegalArgumentException("Some selected items are no longer available in your cart.");
        }

        return selectedItems;
    }

    private void moveRemainingItemsToFreshCart(User user,
                                               Cart currentCart,
                                               List<CartItem> currentCartItems,
                                               List<CartItem> remainingCartItems) {
        if (remainingCartItems.isEmpty()) {
            cartItemRepository.deleteAll(currentCartItems);
            return;
        }

        Cart freshCart = new Cart();
        freshCart.setUser(user);
        freshCart.setStatus(CartStatus.ACTIVE);
        Cart savedCart = cartRepository.save(freshCart);

        List<CartItem> copiedItems = remainingCartItems.stream()
                .map(cartItem -> copyCartItem(savedCart, cartItem))
                .toList();
        cartItemRepository.saveAll(copiedItems);
        cartItemRepository.deleteAll(currentCartItems);
    }

    private CartItem copyCartItem(Cart targetCart, CartItem sourceItem) {
        CartItem copiedItem = new CartItem();
        copiedItem.setId(new CartItemId(targetCart.getId(), sourceItem.getVariant().getId()));
        copiedItem.setCart(targetCart);
        copiedItem.setVariant(sourceItem.getVariant());
        copiedItem.setQuantity(sourceItem.getQuantity());
        return copiedItem;
    }

    private OrderSummaryDTO toSummary(Order order) {
        return new OrderSummaryDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getStatus().getDisplayName(),
                formatTimestamp(order.getCreatedAt()),
                order.getShippingAddress(),
                order.getTotalQuantity() == null ? 0L : order.getTotalQuantity(),
                fromMinorAmount(order.getSubtotalAmount()),
                order.getCreatedAt()
        );
    }

    private OrderDetailDTO toDetail(Order order, List<OrderItem> items) {
        List<OrderItemDTO> orderItems = items.stream()
                .map(this::toItemDTO)
                .toList();

        return new OrderDetailDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getStatus().getDisplayName(),
                formatTimestamp(order.getCreatedAt()),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getTotalQuantity() == null ? 0L : order.getTotalQuantity(),
                fromMinorAmount(order.getSubtotalAmount()),
                order.getCreatedAt(),
                orderItems
        );
    }

    private OrderItemDTO toItemDTO(OrderItem item) {
        return new OrderItemDTO(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getVariant() != null ? item.getVariant().getId() : null,
                item.getProductName(),
                item.getProductSlug(),
                item.getProductImageUrl(),
                item.getSku(),
                item.getQuantity(),
                fromMinorAmount(item.getUnitPriceAmount()),
                fromMinorAmount(item.getLineTotalAmount())
        );
    }

    private String generateOrderNumber() {
        String prefix = "SH-" + ORDER_NUMBER_TIME.format(LocalDateTime.now()) + "-";
        String orderNumber;
        do {
            orderNumber = prefix + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase(Locale.ROOT);
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private String normalizeAddress(String shippingAddress) {
        if (shippingAddress == null) {
            return "";
        }
        return shippingAddress.trim();
    }

    private Set<Long> normalizeVariantIds(List<Long> variantIds) {
        if (variantIds == null) {
            return java.util.Collections.emptySet();
        }
        return new LinkedHashSet<>(variantIds.stream()
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    private long defaultAmount(Long amount) {
        return amount == null ? 0L : amount;
    }

    private BigDecimal fromMinorAmount(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, 2);
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return ORDER_DATE_TIME.format(timestamp);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private record CheckoutLine(CartItem cartItem, ProductVariant variant) {
    }

    private static final class CheckoutGroup {

        private final User seller;
        private final List<CheckoutLine> lines = new ArrayList<>();

        private CheckoutGroup(User seller) {
            this.seller = seller;
        }

        private User seller() {
            return seller;
        }

        private List<CheckoutLine> lines() {
            return lines.stream()
                    .sorted(Comparator.comparing(line -> line.variant().getId()))
                    .toList();
        }

        private void add(CartItem cartItem, ProductVariant variant) {
            lines.add(new CheckoutLine(cartItem, variant));
        }
    }
}
