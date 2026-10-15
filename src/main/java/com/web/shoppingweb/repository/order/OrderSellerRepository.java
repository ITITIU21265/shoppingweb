package com.web.shoppingweb.repository.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.order.OrderSeller;

@Repository
public interface OrderSellerRepository extends JpaRepository<OrderSeller, Long> {

    List<OrderSeller> findBySeller_UsernameOrderByCreatedAtDesc(String username);

    List<OrderSeller> findBySeller_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Optional<OrderSeller> findByIdAndSeller_Username(Long id, String username);

    long countBySeller_Username(String username);

    @Query("""
            select count(distinct os.order.user.id)
            from OrderSeller os
            where os.seller.username = :username
            """)
    long countDistinctCustomersBySellerUsername(@Param("username") String username);

    @Query("""
            select coalesce(sum(os.totalAmount), 0)
            from OrderSeller os
            where os.seller.username = :username
            """)
    Long sumTotalAmountBySellerUsername(@Param("username") String username);
}
