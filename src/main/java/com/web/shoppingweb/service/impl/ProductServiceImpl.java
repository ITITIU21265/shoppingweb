package com.web.shoppingweb.service.impl;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.entity.product.CategoryEntity;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;
import com.web.shoppingweb.entity.product.ProductStatus;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.product.ProductVariantStatus;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.product.CategoryRepository;
import com.web.shoppingweb.repository.product.ProductRepository;
import com.web.shoppingweb.repository.product.ProductVariantRepository;
import com.web.shoppingweb.repository.user.UserRepository;
import com.web.shoppingweb.service.ProductService;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public ProductServiceImpl(CategoryRepository categoryRepository,
                              ProductRepository productRepository,
                              ProductVariantRepository productVariantRepository,
                              UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
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
    @Transactional(readOnly = true)
    public Page<Product> getCatalogPage(ProductCategory category, Pageable pageable) {
        if (category == null) {
            return productRepository.findByActiveTrueOrderByFeaturedDescCreatedAtDesc(pageable);
        }
        return productRepository.findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(category, pageable);
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

        Product savedProduct = productRepository.save(product);
        ensureDefaultVariant(savedProduct);
        return savedProduct;
    }

    @Override
    public Product updateProduct(Long productId, ProductFormDTO dto, String username) {
        User actor = getUser(username);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        assertProductOwnerOrAdmin(product, actor);

        ProductCategory category = ProductCategory.fromValue(dto.getCategory());
        CategoryEntity categoryRef = resolveCategoryRef(category);

        product.setCategoryRef(categoryRef);
        product.setName(dto.getName().trim());
        product.setTitle(dto.getName().trim());
        product.setDescription(dto.getDescription().trim());
        product.setPrice(dto.getPrice());
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl().trim());
        product.setFeatured(dto.isFeatured());
        product.setStatus(ProductStatus.ACTIVE);
        product.setActive(true);

        return productRepository.save(product);
    }

    @Override
    public void deleteProduct(Long productId, String username) {
        User actor = getUser(username);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        assertProductOwnerOrAdmin(product, actor);

        product.setActive(false);
        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);
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

    private void ensureDefaultVariant(Product product) {
        if (productVariantRepository.existsByProduct(product)) {
            return;
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("P" + product.getId() + "-DEFAULT");
        variant.setPriceAmount(product.getPrice().movePointRight(2).longValueExact());
        variant.setStockQty(100);
        variant.setDefault(true);
        variant.setStatus(ProductVariantStatus.ACTIVE);
        productVariantRepository.save(variant);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void assertProductOwnerOrAdmin(Product product, User actor) {
        if (hasRole(actor, "ADMIN")) {
            return;
        }
        if (product.getSeller() == null
                || product.getSeller().getId() == null
                || !product.getSeller().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You can only manage your own products.");
        }
    }

    private boolean hasRole(User user, String roleCode) {
        return user.getRoles() != null
                && user.getRoles().stream()
                .anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
    }
}
