package com.web.shoppingweb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductVariant;
import com.web.shoppingweb.entity.ProductVariantStatus;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findFirstByProduct_IdAndStatusOrderByIsDefaultDescIdAsc(Long productId,
                                                                                      ProductVariantStatus status);

    Optional<ProductVariant> findByProductAndIsDefaultTrue(Product product);

    boolean existsByProduct(Product product);
}
