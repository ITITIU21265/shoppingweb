package com.web.shoppingweb.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;
import java.util.List;
import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartStatus;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.product.CategoryEntity;
import com.web.shoppingweb.entity.product.ProductCategory;
import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.repository.product.CategoryRepository;
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

    @Mock
    private CategoryRepository categoryRepository;

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

    @Test
    void fallbackVariantDoesNotInventStockOrScalePrice() {
        User buyer = user(1L, "buyer");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(user(2L, "seller"));
        product.setActive(true);
        product.setPrice(new BigDecimal("299000.00"));
        Cart cart = new Cart();
        cart.setId(40L);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserAndStatus(buyer, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productVariantRepository.save(any())).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setId(30L);
            return variant;
        });
        when(cartItemRepository.findByCartOrderByCreatedAtAsc(cart)).thenAnswer(invocation -> {
            var savedItem = org.mockito.ArgumentCaptor.forClass(com.web.shoppingweb.entity.cart.CartItem.class);
            verify(cartItemRepository).save(savedItem.capture());
            return List.of(savedItem.getValue());
        });
        var result = cartService.addProduct("buyer", 20L, 2);
        var saved = org.mockito.ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(saved.capture());
        assertEquals(0, saved.getValue().getStockQty());
        assertEquals(299000L, saved.getValue().getPriceAmount());
        assertEquals(new BigDecimal("598000"), result.getSubtotal());
    }

    @Test
    void productPriceUpdateSynchronizesAllVariantsAndCategory() {
        User seller = user(2L, "seller");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setPrice(new BigDecimal("199000"));
        product.setCategory(ProductCategory.SHOES);
        CategoryEntity category = new CategoryEntity();
        category.setSlug("t-shirts");
        ProductVariant first = new ProductVariant();
        first.setPriceAmount(199000L);
        first.setStockQty(7);
        ProductVariant second = new ProductVariant();
        second.setPriceAmount(209000L);
        second.setStockQty(0);
        second.setStatus(ProductVariantStatus.INACTIVE);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(categoryRepository.findBySlug("t-shirts")).thenReturn(Optional.of(category));
        when(productVariantRepository.findAllByProduct_IdOrderByIdAsc(20L)).thenReturn(List.of(first, second));
        when(productRepository.save(product)).thenReturn(product);
        ProductServiceImpl service = new ProductServiceImpl(categoryRepository, productRepository,
                productVariantRepository, userRepository);
        service.updateProduct(20L, form(), "seller");
        assertEquals(new BigDecimal("299000"), product.getPrice());
        assertEquals(299000L, first.getPriceAmount());
        assertEquals(299000L, second.getPriceAmount());
        assertEquals(7, first.getStockQty());
        assertEquals(0, second.getStockQty());
        assertEquals(ProductCategory.T_SHIRTS, product.getCategory());
        assertSame(category, product.getCategoryRef());
        verify(productVariantRepository).saveAll(List.of(first, second));
    }

    @Test
    void productCreationUsesWholeVndAndMatchingCategory() {
        User seller = user(2L, "seller");
        CategoryEntity category = new CategoryEntity();
        category.setSlug("t-shirts");
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(categoryRepository.findBySlug("t-shirts")).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(20L);
            return product;
        });
        ProductServiceImpl service = new ProductServiceImpl(categoryRepository, productRepository,
                productVariantRepository, userRepository);
        Product product = service.createProduct(form(), "seller");
        var saved = org.mockito.ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(saved.capture());
        assertEquals(299000L, saved.getValue().getPriceAmount());
        assertEquals(ProductCategory.T_SHIRTS, product.getCategory());
        assertSame(category, product.getCategoryRef());
    }

    private ProductFormDTO form() {
        ProductFormDTO dto = new ProductFormDTO();
        dto.setName("Test shirt");
        dto.setDescription("Test description");
        dto.setPrice(new BigDecimal("299000"));
        dto.setCategory("T_SHIRTS");
        dto.setImageUrl("/shirt.png");
        return dto;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
