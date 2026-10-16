package com.web.shoppingweb.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.web.shoppingweb.entity.cart.Cart;
import com.web.shoppingweb.entity.cart.CartItem;
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
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.SelfPurchaseException;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.cart.CartItemRepository;
import com.web.shoppingweb.repository.cart.CartRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.validation.Validation;

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

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query sizeQuery;

    @Mock
    private Query colorQuery;

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
    void missingDefaultVariantIsRejectedWithoutInventingStockOrSku() {
        User buyer = user(1L, "buyer");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(user(2L, "seller"));
        product.setActive(true);
        product.setPrice(new BigDecimal("299000.00"));
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findFirstByProduct_IdAndStatusOrderByIsDefaultDescIdAsc(
                20L, ProductVariantStatus.ACTIVE)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cartService.addProduct("buyer", 20L, 2));

        assertEquals("Product has no active variant.", error.getMessage());
        verify(productVariantRepository, never()).save(any());
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void addVariantUsesSelectedNonDefaultSkuAndExactPrice() {
        User buyer = user(1L, "buyer");
        Product product = purchasableProduct(20L, user(2L, "seller"));
        ProductVariant variant = variant(31L, product, "P20-V002", 319000L, 5,
                ProductVariantStatus.ACTIVE);
        Cart cart = new Cart();
        cart.setId(40L);
        CartItem[] savedItem = new CartItem[1];

        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserAndStatus(buyer, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndVariant_Id(cart, 31L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any())).thenAnswer(invocation -> {
            savedItem[0] = invocation.getArgument(0);
            return savedItem[0];
        });
        when(cartItemRepository.findByCartOrderByCreatedAtAsc(cart))
                .thenAnswer(invocation -> List.of(savedItem[0]));

        var result = cartService.addVariant("buyer", 20L, 31L, 2);

        assertEquals(2, result.getTotalQuantity());
        assertEquals(new BigDecimal("638000"), result.getSubtotal());
        assertEquals("P20-V002", result.getItems().get(0).getSku());
        assertEquals(new BigDecimal("319000"), result.getItems().get(0).getUnitPrice());
        assertEquals(31L, savedItem[0].getVariant().getId());
    }

    @Test
    void addVariantRejectsVariantOwnedByAnotherProductBeforeCartMutation() {
        User buyer = user(1L, "buyer");
        Product requestedProduct = purchasableProduct(20L, user(2L, "seller"));
        Product otherProduct = purchasableProduct(21L, user(3L, "other-seller"));
        ProductVariant variant = variant(31L, otherProduct, "P21-V001", 319000L, 5,
                ProductVariantStatus.ACTIVE);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(requestedProduct));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(variant));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cartService.addVariant("buyer", 20L, 31L, 1));

        assertEquals("Selected variant does not belong to this product.", error.getMessage());
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void addVariantRejectsMissingVariantBeforeCartMutation() {
        User buyer = user(1L, "buyer");
        Product product = purchasableProduct(20L, user(2L, "seller"));
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.addVariant("buyer", 20L, 999L, 1));
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void addVariantRejectsNonPositiveQuantityBeforeCartMutation() {
        User buyer = user(1L, "buyer");
        Product product = purchasableProduct(20L, user(2L, "seller"));
        ProductVariant variant = variant(31L, product, "P20-V002", 319000L, 5,
                ProductVariantStatus.ACTIVE);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(variant));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cartService.addVariant("buyer", 20L, 31L, 0));

        assertEquals("Quantity must be at least 1.", error.getMessage());
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void addVariantRejectsInactiveAndOutOfStockVariants() {
        User buyer = user(1L, "buyer");
        Product product = purchasableProduct(20L, user(2L, "seller"));
        ProductVariant inactive = variant(30L, product, "P20-V001", 299000L, 3,
                ProductVariantStatus.INACTIVE);
        ProductVariant outOfStock = variant(31L, product, "P20-V002", 319000L, 0,
                ProductVariantStatus.ACTIVE);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findById(30L)).thenReturn(Optional.of(inactive));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(outOfStock));

        assertThrows(IllegalArgumentException.class,
                () -> cartService.addVariant("buyer", 20L, 30L, 1));
        assertThrows(IllegalArgumentException.class,
                () -> cartService.addVariant("buyer", 20L, 31L, 1));
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void addVariantRejectsSelfPurchaseAndCumulativeQuantityAboveStock() {
        User seller = user(2L, "seller");
        Product product = purchasableProduct(20L, seller);
        ProductVariant variant = variant(31L, product, "P20-V002", 319000L, 5,
                ProductVariantStatus.ACTIVE);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        assertThrows(SelfPurchaseException.class,
                () -> cartService.addVariant("seller", 20L, 31L, 1));
        verify(productVariantRepository, never()).findById(any());

        User buyer = user(1L, "buyer");
        Cart cart = new Cart();
        cart.setId(40L);
        CartItem existing = cartItem(cart, variant, 4);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(cartRepository.findByUserAndStatus(buyer, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCartAndVariant_Id(cart, 31L)).thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cartService.addVariant("buyer", 20L, 31L, 2));
        assertEquals("Requested quantity exceeds available stock.", error.getMessage());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateQuantityRejectsUnavailableStock() {
        User buyer = user(1L, "buyer");
        Product product = purchasableProduct(20L, user(2L, "seller"));
        ProductVariant variant = variant(31L, product, "P20-V002", 319000L, 5,
                ProductVariantStatus.ACTIVE);
        Cart cart = new Cart();
        cart.setId(40L);
        CartItem existing = cartItem(cart, variant, 2);
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(buyer));
        when(cartRepository.findByUserAndStatus(buyer, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndVariant_Id(cart, 31L)).thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cartService.updateQuantity("buyer", 31L, 6));

        assertEquals("Requested quantity exceeds available stock.", error.getMessage());
        assertEquals(2, existing.getQuantity());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void metadataOnlyPriceUpdateChangesDefaultVariantButPreservesOtherPricesStockAndFeatured() {
        User seller = user(2L, "seller");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setPrice(new BigDecimal("199000"));
        product.setCategory(ProductCategory.SHOES);
        product.setFeatured(true);
        CategoryEntity category = new CategoryEntity();
        category.setSlug("t-shirts");
        ProductVariant first = new ProductVariant();
        first.setPriceAmount(199000L);
        first.setStockQty(7);
        first.setDefault(true);
        ProductVariant second = new ProductVariant();
        second.setPriceAmount(209000L);
        second.setStockQty(0);
        second.setStatus(ProductVariantStatus.INACTIVE);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(categoryRepository.findBySlug("t-shirts")).thenReturn(Optional.of(category));
        when(productVariantRepository.findAllByProduct_IdOrderByIdAsc(20L)).thenReturn(List.of(first, second));
        when(productRepository.save(product)).thenReturn(product);
        productService().updateProduct(20L, baseForm(), "seller");
        assertEquals(new BigDecimal("299000"), product.getPrice());
        assertEquals(299000L, first.getPriceAmount());
        assertEquals(209000L, second.getPriceAmount());
        assertEquals(7, first.getStockQty());
        assertEquals(0, second.getStockQty());
        assertTrue(product.isFeatured());
        assertEquals(ProductCategory.T_SHIRTS, product.getCategory());
        assertSame(category, product.getCategoryRef());
        verify(productVariantRepository).saveAll(List.of(first));
    }

    @Test
    void productCreationPersistsEveryVariantWithDeterministicSkus() {
        User seller = user(2L, "seller");
        CategoryEntity category = new CategoryEntity();
        category.setSlug("t-shirts");
        stubValidOptions(List.of(1L, 2L), List.of(10L, 11L));
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(categoryRepository.findBySlug("t-shirts")).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(20L);
            return product;
        });
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(
                variant(null, 1L, 10L, "299000", 7),
                variant(null, 2L, 11L, "319000", 3)
        ));

        Product product = productService().createProduct(form, "seller");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductVariant>> saved = ArgumentCaptor.forClass(List.class);
        verify(productVariantRepository).saveAll(saved.capture());
        List<ProductVariant> variants = saved.getValue();
        assertEquals(2, variants.size());
        assertEquals("P20-V001", variants.get(0).getSku());
        assertEquals("P20-V002", variants.get(1).getSku());
        assertTrue(variants.get(0).isDefault());
        assertFalse(variants.get(1).isDefault());
        assertEquals(299000L, variants.get(0).getPriceAmount());
        assertEquals(319000L, variants.get(1).getPriceAmount());
        assertEquals(7, variants.get(0).getStockQty());
        assertEquals(3, variants.get(1).getStockQty());
        assertFalse(product.isFeatured());
        assertEquals(ProductCategory.T_SHIRTS, product.getCategory());
        assertSame(category, product.getCategoryRef());
    }

    @Test
    void nestedVariantBeanValidationReportsFractionalPriceAndNegativeStock() {
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(variant(null, null, 10L, "299000.50", -1)));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Set<String> invalidPaths = validatorFactory.getValidator().validate(form).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            assertTrue(invalidPaths.contains("variants[0].price"));
            assertTrue(invalidPaths.contains("variants[0].stockQty"));
        }
    }

    @Test
    void productCreationRejectsUnknownOptionIdsBeforeWriting() {
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(variant(null, 999L, 10L, "299000", 1)));
        when(entityManager.createNativeQuery("select id from sizes where id in (:ids)")).thenReturn(sizeQuery);
        when(sizeQuery.setParameter(eq("ids"), any())).thenReturn(sizeQuery);
        when(sizeQuery.getResultList()).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> productService().createProduct(form, "seller"));

        assertTrue(error.getMessage().contains("Unknown size ID(s): [999]"));
        verify(productRepository, never()).save(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void productUpdateRejectsVariantOwnedByAnotherProduct() {
        User seller = user(2L, "seller");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setPrice(new BigDecimal("299000"));
        ProductVariant ownedVariant = new ProductVariant();
        ownedVariant.setId(100L);
        ownedVariant.setProduct(product);
        ownedVariant.setSizeId(1L);
        ownedVariant.setColorId(10L);
        ownedVariant.setSku("P20-V001");
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(variant(999L, 1L, 10L, "299000", 2)));
        stubValidOptions(List.of(1L), List.of(10L));
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findAllByProduct_IdOrderByIdAsc(20L)).thenReturn(List.of(ownedVariant));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> productService().updateProduct(20L, form, "seller"));

        assertEquals("Variant ID 999 does not belong to this product.", error.getMessage());
        verify(productRepository, never()).save(any());
        verify(productVariantRepository, never()).saveAll(any());
    }

    @Test
    void productCreationRejectsMissingAndOversizedVariantCollections() {
        ProductFormDTO missing = baseForm();
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> productService().createProduct(missing, "seller"));
        assertEquals("At least one product variant is required.", missingError.getMessage());

        ProductFormDTO oversized = baseForm();
        List<ProductFormDTO.VariantDTO> rows = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            rows.add(variant(null, (long) index + 1, 10L, "299000", 1));
        }
        oversized.setVariants(rows);
        IllegalArgumentException oversizedError = assertThrows(IllegalArgumentException.class,
                () -> productService().createProduct(oversized, "seller"));
        assertEquals("A product cannot have more than 100 variants.", oversizedError.getMessage());
        verifyNoInteractions(entityManager);
    }

    @Test
    void explicitVariantUpdateRetainsOmittedSkusAndTheirPrices() {
        User seller = user(2L, "seller");
        Product product = new Product();
        product.setId(20L);
        product.setSeller(seller);
        product.setPrice(new BigDecimal("199000"));
        CategoryEntity category = new CategoryEntity();
        category.setSlug("t-shirts");
        ProductVariant first = new ProductVariant();
        first.setId(100L); first.setSku("P20-V001"); first.setProduct(product);
        first.setSizeId(1L); first.setColorId(10L); first.setDefault(true);
        first.setPriceAmount(199000L); first.setStockQty(7);
        ProductVariant retained = new ProductVariant();
        retained.setId(101L); retained.setSku("P20-V002"); retained.setProduct(product);
        retained.setSizeId(2L); retained.setColorId(10L);
        retained.setPriceAmount(209000L); retained.setStockQty(4);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(seller));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
        when(categoryRepository.findBySlug("t-shirts")).thenReturn(Optional.of(category));
        when(productVariantRepository.findAllByProduct_IdOrderByIdAsc(20L))
                .thenReturn(List.of(first, retained));
        stubValidOptions(List.of(1L, 3L), List.of(10L));
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(variant(100L, 1L, 10L, "299000", 6),
                variant(null, 3L, 10L, "319000", 2)));

        productService().updateProduct(20L, form, "seller");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductVariant>> saved = ArgumentCaptor.forClass(List.class);
        verify(productVariantRepository).saveAll(saved.capture());
        assertEquals(3, saved.getValue().size());
        assertEquals(299000L, first.getPriceAmount());
        assertEquals(6, first.getStockQty());
        assertEquals(209000L, retained.getPriceAmount());
        assertEquals(4, retained.getStockQty());
        assertEquals("P20-V003", saved.getValue().get(2).getSku());
        assertEquals(319000L, saved.getValue().get(2).getPriceAmount());
        assertEquals(1, saved.getValue().stream().filter(ProductVariant::isDefault).count());
    }

    @Test
    void duplicateVariantOptionsAreRejectedBeforeAnyPersistence() {
        ProductFormDTO form = baseForm();
        form.setVariants(List.of(variant(null, 1L, 10L, "299000", 2),
                variant(null, 1L, 10L, "319000", 4)));
        assertThrows(IllegalArgumentException.class, () -> productService().createProduct(form, "seller"));
        verifyNoInteractions(entityManager, productRepository, productVariantRepository);
    }

    private ProductFormDTO baseForm() {
        ProductFormDTO dto = new ProductFormDTO();
        dto.setName("Test shirt");
        dto.setDescription("Test description");
        dto.setPrice(new BigDecimal("299000"));
        dto.setCategory("T_SHIRTS");
        dto.setImageUrl("/shirt.png");
        return dto;
    }

    private ProductFormDTO.VariantDTO variant(Long id,
                                              Long sizeId,
                                              Long colorId,
                                              String price,
                                              Integer stockQty) {
        ProductFormDTO.VariantDTO row = new ProductFormDTO.VariantDTO();
        row.setId(id);
        row.setSizeId(sizeId);
        row.setColorId(colorId);
        row.setPrice(new BigDecimal(price));
        row.setStockQty(stockQty);
        return row;
    }

    private ProductServiceImpl productService() {
        ProductServiceImpl service = new ProductServiceImpl(categoryRepository, productRepository,
                productVariantRepository, userRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    private void stubValidOptions(List<Long> sizeIds, List<Long> colorIds) {
        when(entityManager.createNativeQuery("select id from sizes where id in (:ids)")).thenReturn(sizeQuery);
        when(sizeQuery.setParameter(eq("ids"), any())).thenReturn(sizeQuery);
        when(sizeQuery.getResultList()).thenReturn(new ArrayList<>(sizeIds));
        when(entityManager.createNativeQuery("select id from colors where id in (:ids)")).thenReturn(colorQuery);
        when(colorQuery.setParameter(eq("ids"), any())).thenReturn(colorQuery);
        when(colorQuery.getResultList()).thenReturn(new ArrayList<>(colorIds));
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Product purchasableProduct(Long id, User seller) {
        Product product = new Product();
        product.setId(id);
        product.setSeller(seller);
        product.setName("Test product");
        product.setSlug("test-product-" + id);
        product.setActive(true);
        return product;
    }

    private ProductVariant variant(Long id,
                                   Product product,
                                   String sku,
                                   long priceAmount,
                                   int stockQty,
                                   ProductVariantStatus status) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPriceAmount(priceAmount);
        variant.setStockQty(stockQty);
        variant.setStatus(status);
        return variant;
    }

    private CartItem cartItem(Cart cart, ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setVariant(variant);
        item.setQuantity(quantity);
        return item;
    }
}
