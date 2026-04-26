package com.web.shoppingweb.repository.saved;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.saved.SavedItem;
import com.web.shoppingweb.entity.user.User;

@Repository
public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {

    List<SavedItem> findByUserOrderByCreatedAtDesc(User user);

    Optional<SavedItem> findByUserAndProduct(User user, Product product);

    boolean existsByUserAndProduct(User user, Product product);

    long countByUser(User user);
}
