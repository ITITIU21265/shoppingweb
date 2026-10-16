package com.web.shoppingweb.repository.product;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findFirstByProduct_IdAndStatusOrderByIsDefaultDescIdAsc(Long productId,
                                                                                      ProductVariantStatus status);

    Optional<ProductVariant> findByProductAndIsDefaultTrue(Product product);

    @Query(value = """
            select pv.id, s.label, c.name
            from product_variants pv
            left join sizes s on s.id = pv.size_id
            left join colors c on c.id = pv.color_id
            where pv.id in (:ids)
            """, nativeQuery = true)
    List<Object[]> findOptionLabelsByVariantIds(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v
            from ProductVariant v
            join fetch v.product p
            join fetch p.seller
            where v.id in :ids
            order by v.id
            """)
    List<ProductVariant> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    // Serialize price edits with checkout stock updates on the same variants.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductVariant> findAllByProduct_IdOrderByIdAsc(Long productId);

    boolean existsByProduct(Product product);

    long countByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatus(String username,
                                                                         Integer stockQty,
                                                                         ProductVariantStatus status);

    long countByStockQtyLessThanEqualAndStatus(Integer stockQty,
                                               ProductVariantStatus status);

    List<ProductVariant> findTop5ByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatusOrderByStockQtyAscIdAsc(
            String username,
            Integer stockQty,
            ProductVariantStatus status
    );

    List<ProductVariant> findTop6ByStockQtyLessThanEqualAndStatusOrderByStockQtyAscIdAsc(
            Integer stockQty,
            ProductVariantStatus status
    );
}
