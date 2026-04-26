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
