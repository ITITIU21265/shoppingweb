package com.web.shoppingweb.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.cart.CartItemResponseDTO;
import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartItem;
import com.web.shoppingweb.entity.cart.CartItemId;
import com.web.shoppingweb.entity.cart.CartStatus;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.product.ProductStatus;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.exception.SelfPurchaseException;
import com.web.shoppingweb.repository.cart.CartItemRepository;
import com.web.shoppingweb.repository.cart.CartRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;
import com.web.shoppingweb.service.CartService;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository cartItemRepository,
                           ProductRepository productRepository,
                           ProductVariantRepository productVariantRepository,
                           UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartSummaryDTO getActiveCart(String username) {
        User user = getUser(username);
        return cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .map(this::buildSummary)
                .orElseGet(CartSummaryDTO::new);
    }

    @Override
    @Transactional(readOnly = true)
    public CartSummaryDTO getSelectedCart(String username, List<Long> variantIds) {
        Set<Long> selectedVariantIds = normalizeVariantIds(variantIds);
        if (selectedVariantIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to checkout.");
        }

        User user = getUser(username);
        Cart cart = cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Your cart is empty."));

        List<CartItem> cartItems = cartItemRepository.findByCartOrderByCreatedAtAsc(cart);
        Map<Long, OptionLabels> optionLabels = loadOptionLabels(cartItems);
        List<CartItemResponseDTO> selectedItems = cartItems.stream()
                .map(item -> toItemResponse(item, optionLabels))
                .filter(item -> selectedVariantIds.contains(item.getVariantId()))
                .toList();

        if (selectedItems.isEmpty() || selectedItems.size() != selectedVariantIds.size()) {
            throw new IllegalArgumentException("Some selected items are no longer available in your cart.");
        }

        return buildSummary(selectedItems);
    }

    @Override
    public CartSummaryDTO addProduct(String username, Long productId, int quantity) {
        User user = getUser(username);
        Product product = getPurchasableProduct(productId);
        validateNotSelfPurchase(user, product);
        ProductVariant variant = resolveDefaultVariant(product);

        return addValidatedVariant(user, product, variant, quantity);
    }

    @Override
    public CartSummaryDTO addVariant(String username, Long productId, Long variantId, int quantity) {
        User user = getUser(username);
        Product product = getPurchasableProduct(productId);
        validateNotSelfPurchase(user, product);

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        if (variant.getProduct() == null
                || !Objects.equals(product.getId(), variant.getProduct().getId())) {
            throw new IllegalArgumentException("Selected variant does not belong to this product.");
        }

        return addValidatedVariant(user, product, variant, quantity);
    }

    private CartSummaryDTO addValidatedVariant(User user,
                                               Product product,
                                               ProductVariant variant,
                                               int quantity) {
        validatePositiveQuantity(quantity);
        validateVariantAvailability(product, variant, quantity);

        Cart cart = getOrCreateActiveCart(user);

        CartItem cartItem = cartItemRepository.findByCartAndVariant_Id(cart, variant.getId())
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setId(new CartItemId(cart.getId(), variant.getId()));
                    newItem.setCart(cart);
                    newItem.setVariant(variant);
                    newItem.setQuantity(0);
                    return newItem;
                });

        int existingQuantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
        long requestedTotal = (long) existingQuantity + quantity;
        if (requestedTotal > variant.getStockQty()) {
            throw new IllegalArgumentException("Requested quantity exceeds available stock.");
        }
        cartItem.setQuantity((int) requestedTotal);
        cartItemRepository.save(cartItem);
        return buildSummary(cart);
    }

    @Override
    public CartSummaryDTO updateQuantity(String username, Long variantId, int quantity) {
        User user = getUser(username);
        Cart cart = cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active cart not found"));

        CartItem cartItem = cartItemRepository.findByCartAndVariant_Id(cart, variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
        } else {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            if (!isPurchasable(product)) {
                throw new IllegalArgumentException("Product is no longer available.");
            }
            validateNotSelfPurchase(user, product);
            validateVariantAvailability(product, variant, quantity);
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return buildSummary(cart);
    }

    @Override
    public CartSummaryDTO removeItem(String username, Long variantId) {
        User user = getUser(username);
        Cart cart = cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Active cart not found"));

        cartItemRepository.findByCartAndVariant_Id(cart, variantId)
                .ifPresent(cartItemRepository::delete);

        return buildSummary(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCartItems(String username) {
        User user = getUser(username);
        return cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .map(cartItemRepository::sumQuantityByCart)
                .orElse(0L);
    }

    private Cart getOrCreateActiveCart(User user) {
        return cartRepository.findByUserAndStatus(user, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setStatus(CartStatus.ACTIVE);
                    return cartRepository.save(cart);
                });
    }

    private ProductVariant resolveDefaultVariant(Product product) {
        ProductVariant variant = productVariantRepository.findFirstByProduct_IdAndStatusOrderByIsDefaultDescIdAsc(
                        product.getId(),
                        ProductVariantStatus.ACTIVE
                )
                .orElseThrow(() -> new IllegalArgumentException("Product has no active variant."));
        validateVariantAvailability(product, variant, 1);
        return variant;
    }

    private CartSummaryDTO buildSummary(Cart cart) {
        List<CartItem> cartItems = cartItemRepository.findByCartOrderByCreatedAtAsc(cart);
        Map<Long, OptionLabels> optionLabels = loadOptionLabels(cartItems);
        List<CartItemResponseDTO> items = cartItems.stream()
                .map(item -> toItemResponse(item, optionLabels))
                .toList();
        return buildSummary(items);
    }

    private CartSummaryDTO buildSummary(List<CartItemResponseDTO> items) {
        long totalQuantity = items.stream()
                .mapToLong(CartItemResponseDTO::getQuantity)
                .sum();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponseDTO::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartSummaryDTO(items, totalQuantity, subtotal);
    }

    private Set<Long> normalizeVariantIds(List<Long> variantIds) {
        if (variantIds == null) {
            return java.util.Collections.emptySet();
        }
        return new LinkedHashSet<>(variantIds.stream()
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    private Map<Long, OptionLabels> loadOptionLabels(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> variantIds = cartItems.stream()
                .map(CartItem::getVariant)
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> rows = productVariantRepository.findOptionLabelsByVariantIds(variantIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OptionLabels> labelsByVariantId = new HashMap<>();
        for (Object[] row : rows) {
            if (row != null && row.length >= 3 && row[0] instanceof Number id) {
                labelsByVariantId.put(id.longValue(), new OptionLabels(
                        row[1] == null ? null : row[1].toString(),
                        row[2] == null ? null : row[2].toString()
                ));
            }
        }
        return labelsByVariantId;
    }

    private CartItemResponseDTO toItemResponse(CartItem cartItem, Map<Long, OptionLabels> optionLabels) {
        ProductVariant variant = cartItem.getVariant();
        Product product = variant.getProduct();
        OptionLabels labels = optionLabels.getOrDefault(variant.getId(), OptionLabels.EMPTY);
        BigDecimal unitPrice = toDisplayAmount(variant.getPriceAmount());
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return new CartItemResponseDTO(
                variant.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getImageUrl(),
                resolveSellerName(product),
                variant.getSku(),
                labels.sizeLabel(),
                labels.colorName(),
                cartItem.getQuantity(),
                unitPrice,
                lineTotal
        );
    }

    private String resolveSellerName(Product product) {
        User seller = product.getSeller();
        if (seller == null) {
            return "Unknown Seller";
        }
        if (seller.getFullName() != null && !seller.getFullName().isBlank()) {
            return seller.getFullName();
        }
        if (seller.getUsername() != null && !seller.getUsername().isBlank()) {
            return seller.getUsername();
        }
        return "Unknown Seller";
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getPurchasableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!isPurchasable(product)) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private boolean isPurchasable(Product product) {
        return product != null
                && product.isActive()
                && product.getStatus() == ProductStatus.ACTIVE;
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }
    }

    private void validateVariantAvailability(Product product, ProductVariant variant, int quantity) {
        if (variant.getProduct() == null
                || !Objects.equals(product.getId(), variant.getProduct().getId())) {
            throw new IllegalArgumentException("Selected variant does not belong to this product.");
        }
        if (variant.getStatus() != ProductVariantStatus.ACTIVE) {
            throw new IllegalArgumentException("Selected variant is no longer available.");
        }
        int stockQty = variant.getStockQty() == null ? 0 : variant.getStockQty();
        if (stockQty < quantity) {
            throw new IllegalArgumentException("Requested quantity exceeds available stock.");
        }
    }

    private void validateNotSelfPurchase(User buyer, Product product) {
        User seller = product.getSeller();
        if (buyer.getId() != null
                && seller != null
                && seller.getId() != null
                && Objects.equals(buyer.getId(), seller.getId())) {
            throw new SelfPurchaseException(
                    "Sellers cannot add their own products to the shopping cart."
            );
        }
    }

    private BigDecimal toDisplayAmount(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount);
    }

    private record OptionLabels(String sizeLabel, String colorName) {
        private static final OptionLabels EMPTY = new OptionLabels(null, null);
    }
}
