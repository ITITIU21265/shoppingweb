package com.web.shoppingweb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByFeaturedDescCreatedAtDesc();

    List<Product> findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(ProductCategory category);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);
}
