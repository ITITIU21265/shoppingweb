package com.web.shoppingweb.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.CartItemResponseDTO;
import com.web.shoppingweb.dto.CartSummaryDTO;
import com.web.shoppingweb.entity.Cart;
import com.web.shoppingweb.entity.CartItem;
import com.web.shoppingweb.entity.CartItemId;
import com.web.shoppingweb.entity.CartStatus;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductVariant;
import com.web.shoppingweb.entity.ProductVariantStatus;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.CartItemRepository;
import com.web.shoppingweb.repository.CartRepository;
import com.web.shoppingweb.repository.ProductRepository;
import com.web.shoppingweb.repository.ProductVariantRepository;
import com.web.shoppingweb.repository.UserRepository;

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
    public CartSummaryDTO addProduct(String username, Long productId, int quantity) {
        if (quantity < 1) {
            quantity = 1;
        }

        User user = getUser(username);
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Cart cart = getOrCreateActiveCart(user);
        ProductVariant variant = resolveDefaultVariant(product);

        CartItem cartItem = cartItemRepository.findByCartAndVariant_Id(cart, variant.getId())
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setId(new CartItemId(cart.getId(), variant.getId()));
                    newItem.setCart(cart);
                    newItem.setVariant(variant);
                    newItem.setQuantity(0);
                    return newItem;
                });

        cartItem.setQuantity(cartItem.getQuantity() + quantity);
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
        return productVariantRepository.findFirstByProduct_IdAndStatusOrderByIsDefaultDescIdAsc(
                        product.getId(),
                        ProductVariantStatus.ACTIVE
                )
                .orElseGet(() -> createDefaultVariant(product));
    }

    private ProductVariant createDefaultVariant(Product product) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("P" + product.getId() + "-DEFAULT");
        variant.setPriceAmount(toMinorAmount(product.getPrice()));
        variant.setStockQty(100);
        variant.setDefault(true);
        variant.setStatus(ProductVariantStatus.ACTIVE);
        return productVariantRepository.save(variant);
    }

    private CartSummaryDTO buildSummary(Cart cart) {
        List<CartItemResponseDTO> items = cartItemRepository.findByCartOrderByCreatedAtAsc(cart).stream()
                .map(this::toItemResponse)
                .toList();

        long totalQuantity = items.stream()
                .mapToLong(CartItemResponseDTO::getQuantity)
                .sum();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponseDTO::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartSummaryDTO(items, totalQuantity, subtotal);
    }

    private CartItemResponseDTO toItemResponse(CartItem cartItem) {
        ProductVariant variant = cartItem.getVariant();
        Product product = variant.getProduct();
        BigDecimal unitPrice = fromMinorAmount(variant.getPriceAmount());
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return new CartItemResponseDTO(
                variant.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getImageUrl(),
                cartItem.getQuantity(),
                unitPrice,
                lineTotal
        );
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private long toMinorAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal fromMinorAmount(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, 2);
    }
}
