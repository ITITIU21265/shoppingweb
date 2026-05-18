package com.web.shoppingweb.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;

public interface ProductService {

    List<Product> getCatalog(ProductCategory category);

    Page<Product> getCatalogPage(ProductCategory category, String keyword, Pageable pageable);

    List<ProductCategory> getAvailableCategories();

    Product getProductDetail(String slug);

    Product createProduct(ProductFormDTO dto, String sellerUsername);

    Product updateProduct(Long productId, ProductFormDTO dto, String username);

    void deleteProduct(Long productId, String username);

    long countProducts();
}
