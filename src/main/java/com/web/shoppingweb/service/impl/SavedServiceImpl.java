package com.web.shoppingweb.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.saved.SavedItem;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.saved.SavedItemRepository;
import com.web.shoppingweb.repository.user.UserRepository;
import com.web.shoppingweb.service.SavedService;

@Service
@Transactional
public class SavedServiceImpl implements SavedService {

    private final SavedItemRepository savedItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public SavedServiceImpl(SavedItemRepository savedItemRepository,
                            UserRepository userRepository,
                            ProductRepository productRepository) {
        this.savedItemRepository = savedItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getSavedProducts(String username) {
        User user = getUser(username);
        return savedItemRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(SavedItem::getProduct)
                .filter(Product::isActive)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getSavedProductIds(String username) {
        User user = getUser(username);
        return savedItemRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(savedItem -> savedItem.getProduct().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean toggleSavedProduct(String username, Long productId) {
        User user = getUser(username);
        Product product = getProduct(productId);

        return savedItemRepository.findByUserAndProduct(user, product)
                .map(existing -> {
                    savedItemRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    SavedItem savedItem = new SavedItem();
                    savedItem.setUser(user);
                    savedItem.setProduct(product);
                    savedItemRepository.save(savedItem);
                    return true;
                });
    }

    @Override
    public void removeSavedProduct(String username, Long productId) {
        User user = getUser(username);
        Product product = getProduct(productId);
        savedItemRepository.findByUserAndProduct(user, product).ifPresent(savedItemRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSavedItems(String username) {
        User user = getUser(username);
        return savedItemRepository.countByUser(user);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
}
