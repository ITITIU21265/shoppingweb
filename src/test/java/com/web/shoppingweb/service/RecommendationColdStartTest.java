package com.web.shoppingweb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Pageable;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.product.ProductRepository;

@ExtendWith(MockitoExtension.class)
class RecommendationColdStartTest {

    private static final Long TARGET_ID = 100L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ProductRecommendationService recommendationService;

    @Test
    void repositoryUsesFirstPageToLimitNativeCacheQuery() {
        Product related = product(200L, "Linen shirt", "soft linen shirt");
        List<Product> cachedProducts = List.of(related);
        doCallRealMethod().when(productRepository).findRecommendedProducts(TARGET_ID, 4);
        when(productRepository.findRecommendedProductsFromCache(eq(TARGET_ID), any(Pageable.class)))
                .thenReturn(cachedProducts);

        assertEquals(cachedProducts, recommendationService.getRelatedProducts(TARGET_ID, 4));

        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findRecommendedProductsFromCache(eq(TARGET_ID), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(4, page.getValue().getPageSize());
        assertTrue(page.getValue().getSort().isUnsorted());
        verify(productRepository, never()).findAllById(any());
    }

    @Test
    void returnsPartialCacheResultInDatabaseRankOrderWithoutUsingInMemoryEngine() {
        Product firstByRank = product(202L, "Linen blue shirt", "breathable casual linen shirt");
        Product secondByRank = product(201L, "Linen white shirt", "breathable casual linen shirt");
        List<Product> cachedProducts = List.of(firstByRank, secondByRank);
        when(productRepository.findRecommendedProducts(TARGET_ID, 4)).thenReturn(cachedProducts);

        List<Product> result = recommendationService.getRelatedProducts(TARGET_ID, 4);

        assertEquals(cachedProducts, result);
        assertSame(firstByRank, result.get(0));
        assertSame(secondByRank, result.get(1));
        verify(productRepository).findRecommendedProducts(TARGET_ID, 4);
        verify(productRepository, never()).findAllById(any());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void fallsBackToInMemoryEngineWhenCacheHasNoRows() {
        Product target = product(TARGET_ID, "Linen shirt", "soft blue linen shirt");
        Product related = product(200L, "Linen jacket", "soft blue linen jacket");
        Product unrelated = product(300L, "Running shoes", "durable athletic shoes");
        trainInMemoryEngine(target, related, unrelated);
        when(productRepository.findRecommendedProducts(TARGET_ID, 4)).thenReturn(List.of());
        when(productRepository.findAllById(List.of(related.getId()))).thenReturn(List.of(related));

        List<Product> result = recommendationService.getRelatedProducts(TARGET_ID, 4);

        assertEquals(List.of(related), result);
        verify(productRepository).findRecommendedProducts(TARGET_ID, 4);
        verify(productRepository).findAllById(List.of(related.getId()));
    }

    @Test
    void fallsBackToInMemoryEngineOnlyWhenCacheTableIsMissing() {
        Product target = product(TARGET_ID, "Linen shirt", "soft blue linen shirt");
        Product related = product(200L, "Linen jacket", "soft blue linen jacket");
        Product unrelated = product(300L, "Running shoes", "durable athletic shoes");
        trainInMemoryEngine(target, related, unrelated);
        SQLException missingTable = new SQLException("Table does not exist", "42S02", 1146);
        when(productRepository.findRecommendedProducts(TARGET_ID, 4))
                .thenThrow(new InvalidDataAccessResourceUsageException("cache table missing", missingTable));
        when(productRepository.findAllById(List.of(related.getId()))).thenReturn(List.of(related));

        List<Product> result = recommendationService.getRelatedProducts(TARGET_ID, 4);

        assertEquals(List.of(related), result);
        verify(productRepository).findAllById(List.of(related.getId()));
    }

    @Test
    void propagatesUnrelatedDatabaseErrorsInsteadOfHidingThemAsFallbacks() {
        SQLException syntaxError = new SQLException("Bad SQL", "42000", 1064);
        InvalidDataAccessResourceUsageException exception =
                new InvalidDataAccessResourceUsageException("bad query", syntaxError);
        when(productRepository.findRecommendedProducts(TARGET_ID, 4)).thenThrow(exception);

        InvalidDataAccessResourceUsageException thrown = assertThrows(
                InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.getRelatedProducts(TARGET_ID, 4));

        assertSame(exception, thrown);
        verify(productRepository, never()).findAllById(any());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void invalidArgumentsReturnEmptyWithoutAccessingRepositories() {
        assertEquals(List.of(), recommendationService.getRelatedProducts(null, 4));
        assertEquals(List.of(), recommendationService.getRelatedProducts(TARGET_ID, 0));
        assertEquals(List.of(), recommendationService.getRelatedProducts(TARGET_ID, -1));

        verifyNoInteractions(productRepository, orderItemRepository);
    }

    private void trainInMemoryEngine(Product target, Product related, Product unrelated) {
        when(productRepository.findAll()).thenReturn(List.of(target, related, unrelated));
        when(orderItemRepository.findAll()).thenReturn(List.of());
        recommendationService.trainModel();
    }

    private Product product(Long id, String name, String description) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription(description);
        return product;
    }
}
