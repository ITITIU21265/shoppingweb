package com.web.shoppingweb.repository.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsBySlug(String slug);

    long countBySeller_Username(String username);

    long countBySeller_UsernameAndFeaturedTrue(String username);
}
