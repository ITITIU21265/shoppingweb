package com.web.shoppingweb.repository.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByFeaturedDescCreatedAtDesc();

    List<Product> findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(ProductCategory category);

    Page<Product> findByActiveTrueOrderByFeaturedDescCreatedAtDesc(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(ProductCategory category, Pageable pageable);

    Page<Product> findByActiveTrueAndNameContainingIgnoreCaseOrderByFeaturedDescCreatedAtDesc(String keyword, Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryAndNameContainingIgnoreCaseOrderByFeaturedDescCreatedAtDesc(ProductCategory category,
                                                                                                         String keyword,
                                                                                                         Pageable pageable);

    List<Product> findBySeller_UsernameOrderByCreatedAtDesc(String username);

    List<Product> findTop5BySeller_UsernameOrderByCreatedAtDesc(String username);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    @Query(value = """
            SELECT p.*
            FROM product_recommendations pr
            JOIN products p ON p.id = pr.recommended_product_id
            WHERE pr.target_product_id = :targetId
              AND p.active = 1
              AND p.status = 'ACTIVE'
            ORDER BY pr.rank_order ASC
            """, nativeQuery = true)
    List<Product> findRecommendedProductsFromCache(@Param("targetId") Long targetId,
                                                   Pageable pageable);

    default List<Product> findRecommendedProducts(Long targetId, int limit) {
        return findRecommendedProductsFromCache(targetId, PageRequest.of(0, limit));
    }

    boolean existsBySlug(String slug);

    long countBySeller_Username(String username);

    long countBySeller_UsernameAndFeaturedTrue(String username);
}
