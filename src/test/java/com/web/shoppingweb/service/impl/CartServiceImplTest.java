package com.web.shoppingweb.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.SelfPurchaseException;
import com.web.shoppingweb.repository.cart.CartItemRepository;
import com.web.shoppingweb.repository.cart.CartRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void addProductRejectsSellerOwnProductBeforeCartMutation() {
        User seller = user(10L, "seller");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setActive(true);

        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        assertThrows(SelfPurchaseException.class, () -> cartService.addProduct("seller", 20L, 1));

        verifyNoInteractions(cartRepository, cartItemRepository, productVariantRepository);
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
