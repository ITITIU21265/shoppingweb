package com.web.shoppingweb.repository.supplier;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.entity.user.User;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findFirstBySellerOrderByCreatedAtDesc(User seller);

    Optional<Supplier> findFirstBySellerAndStatusOrderByCreatedAtDesc(User seller, SupplierStatus status);

    Optional<Supplier> findFirstBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    Optional<Supplier> findFirstBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, SupplierStatus status);
}
