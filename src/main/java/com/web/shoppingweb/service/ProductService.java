package com.web.shoppingweb.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.web.shoppingweb.dto.ProductFormDTO;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;

public interface ProductService {

    List<Product> getCatalog(ProductCategory category);

    Page<Product> getCatalogPage(ProductCategory category, Pageable pageable);

    List<ProductCategory> getAvailableCategories();

    Product getProductDetail(String slug);

    Product createProduct(ProductFormDTO dto, String sellerUsername);

    long countProducts();
}
