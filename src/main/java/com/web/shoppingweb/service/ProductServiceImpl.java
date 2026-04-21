package com.web.shoppingweb.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.ProductFormDTO;
import com.web.shoppingweb.entity.CategoryEntity;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;
import com.web.shoppingweb.entity.ProductStatus;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.CategoryRepository;
import com.web.shoppingweb.repository.ProductRepository;
import com.web.shoppingweb.repository.UserRepository;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductServiceImpl(CategoryRepository categoryRepository,
                              ProductRepository productRepository,
                              UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getCatalog(ProductCategory category) {
        if (category == null) {
            return productRepository.findByActiveTrueOrderByFeaturedDescCreatedAtDesc();
        }
        return productRepository.findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(category);
    }

    @Override
    public List<ProductCategory> getAvailableCategories() {
        return List.of(ProductCategory.values());
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductDetail(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Override
    public Product createProduct(ProductFormDTO dto, String sellerUsername) {
        User seller = userRepository.findByUsername(sellerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        ProductCategory category = ProductCategory.fromValue(dto.getCategory());
        CategoryEntity categoryRef = resolveCategoryRef(category);

        Product product = new Product();
        product.setSeller(seller);
        product.setCategoryRef(categoryRef);
        product.setName(dto.getName().trim());
        product.setTitle(dto.getName().trim());
        product.setDescription(dto.getDescription().trim());
        product.setPrice(dto.getPrice());
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl().trim());
        product.setFeatured(dto.isFeatured());
        product.setActive(true);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCurrency("VND");
        product.setSlug(generateUniqueSlug(dto.getName()));

        return productRepository.save(product);
    }

    @Override
    public long countProducts() {
        return productRepository.count();
    }

    private String generateUniqueSlug(String rawName) {
        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        String baseSlug = normalized.isBlank() ? "product" : normalized;
        String candidate = baseSlug;
        int suffix = 2;

        while (productRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }

        return candidate;
    }

    private CategoryEntity resolveCategoryRef(ProductCategory category) {
        String slug = category.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found for slug: " + slug));
    }
}
