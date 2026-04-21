package com.web.shoppingweb.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.CheckoutRequestDTO;
import com.web.shoppingweb.dto.OrderDetailDTO;
import com.web.shoppingweb.dto.OrderItemDTO;
import com.web.shoppingweb.dto.OrderSummaryDTO;
import com.web.shoppingweb.entity.Cart;
import com.web.shoppingweb.entity.CartItem;
import com.web.shoppingweb.entity.CartItemId;
import com.web.shoppingweb.entity.CartStatus;
import com.web.shoppingweb.entity.Order;
import com.web.shoppingweb.entity.OrderItem;
import com.web.shoppingweb.entity.OrderStatus;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductVariant;
import com.web.shoppingweb.entity.ProductVariantStatus;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.CartItemRepository;
import com.web.shoppingweb.repository.CartRepository;
import com.web.shoppingweb.repository.OrderItemRepository;
import com.web.shoppingweb.repository.OrderRepository;
import com.web.shoppingweb.repository.UserRepository;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter ORDER_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
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

        String shippingAddress = normalizeAddress(request.getShippingAddress());
        long subtotalAmount = 0L;
        int totalQuantity = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = new Order();
        order.setUser(user);
        order.setCart(cart);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCustomerName(user.getFullName());
        order.setCustomerEmail(user.getEmail());
        order.setShippingAddress(shippingAddress);

        for (CartItem cartItem : selectedCartItems) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            validateCheckoutItem(product, variant, cartItem.getQuantity());

            long unitPriceAmount = defaultAmount(variant.getPriceAmount());
            long lineTotalAmount = Math.multiplyExact(unitPriceAmount, cartItem.getQuantity());
            subtotalAmount = Math.addExact(subtotalAmount, lineTotalAmount);
            totalQuantity = Math.addExact(totalQuantity, cartItem.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setVariant(variant);
            orderItem.setProductName(product.getName());
            orderItem.setProductSlug(product.getSlug());
            orderItem.setProductImageUrl(product.getImageUrl());
            orderItem.setSku(variant.getSku());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPriceAmount(unitPriceAmount);
            orderItem.setLineTotalAmount(lineTotalAmount);
            orderItems.add(orderItem);

            int remainingStock = variant.getStockQty() - cartItem.getQuantity();
            variant.setStockQty(remainingStock);
        }

        order.setTotalQuantity(totalQuantity);
        order.setSubtotalAmount(subtotalAmount);
        Order savedOrder = orderRepository.save(order);
        orderItemRepository.saveAll(orderItems);

        moveRemainingItemsToFreshCart(user, cart, cartItems, remainingCartItems);
        cart.setStatus(CartStatus.ORDERED);
        cartRepository.save(cart);

        return toDetail(savedOrder, orderItems);
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
}
