package com.web.shoppingweb.service;

import java.util.List;
import java.util.Set;

import com.web.shoppingweb.entity.Product;

public interface SavedService {

    List<Product> getSavedProducts(String username);

    Set<Long> getSavedProductIds(String username);

    boolean toggleSavedProduct(String username, Long productId);

    void removeSavedProduct(String username, Long productId);

    long countSavedItems(String username);
}
