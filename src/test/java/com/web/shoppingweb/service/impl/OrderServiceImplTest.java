package com.web.shoppingweb.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.web.shoppingweb.dto.order.CheckoutRequestDTO;
import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartItem;
import com.web.shoppingweb.entity.cart.CartItemId;
import com.web.shoppingweb.entity.cart.CartStatus;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.SelfPurchaseException;
import com.web.shoppingweb.repository.cart.CartItemRepository;
import com.web.shoppingweb.repository.cart.CartRepository;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.order.OrderRepository;
import com.web.shoppingweb.repository.order.OrderSellerRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderSellerRepository orderSellerRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void checkoutRejectsSellerOwnInventoryAfterVariantLock() {
        User seller = user(10L, "seller");

        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setId(30L);
        variant.setProduct(product);
        variant.setStatus(ProductVariantStatus.ACTIVE);
        variant.setStockQty(5);
        variant.setPriceAmount(1000L);

        Cart cart = new Cart();
        cart.setId(40L);
        cart.setUser(seller);
        cart.setStatus(CartStatus.ACTIVE);

        CartItem cartItem = new CartItem();
        cartItem.setId(new CartItemId(40L, 30L));
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(1);

        CheckoutRequestDTO request = new CheckoutRequestDTO();
        request.setSelectedVariantIds(List.of(30L));
        request.setShippingAddress("Test address");

        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(cartRepository.findByUserAndStatus(seller, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartOrderByCreatedAtAsc(cart)).thenReturn(List.of(cartItem));
        when(productVariantRepository.findAllByIdInForUpdate(List.of(30L))).thenReturn(List.of(variant));

        assertThrows(SelfPurchaseException.class, () -> orderService.checkout("seller", request));

        verifyNoInteractions(orderRepository, orderSellerRepository, orderItemRepository);
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
