package com.web.shoppingweb.service;

import java.util.List;

import com.web.shoppingweb.dto.ProductFormDTO;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;

public interface ProductService {

    List<Product> getCatalog(ProductCategory category);

    List<ProductCategory> getAvailableCategories();

    Product getProductDetail(String slug);

    Product createProduct(ProductFormDTO dto);

    long countProducts();
}
