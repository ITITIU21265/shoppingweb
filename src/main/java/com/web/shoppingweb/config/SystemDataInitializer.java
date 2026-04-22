package com.web.shoppingweb.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;
import com.web.shoppingweb.entity.ProductStatus;
import com.web.shoppingweb.entity.ProductVariant;
import com.web.shoppingweb.entity.ProductVariantStatus;
import com.web.shoppingweb.entity.Role;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.entity.UserStatus;
import com.web.shoppingweb.entity.CategoryEntity;
import com.web.shoppingweb.repository.CategoryRepository;
import com.web.shoppingweb.repository.ProductRepository;
import com.web.shoppingweb.repository.ProductVariantRepository;
import com.web.shoppingweb.repository.RoleRepository;
import com.web.shoppingweb.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "app.seed.system-data", havingValue = "true")
public class SystemDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemDataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PasswordEncoder passwordEncoder;

    public SystemDataInitializer(RoleRepository roleRepository,
                                 UserRepository userRepository,
                                 CategoryRepository categoryRepository,
                                 ProductRepository productRepository,
                                 ProductVariantRepository productVariantRepository,
                                 PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (hasExistingData()) {
            log.info("Skipping SystemDataInitializer because existing database records were detected.");
            return;
        }

        ensureRole("ADMIN", "Administrator");
        ensureRole("CUSTOMER", "Customer");
        ensureRole("SELLER", "Seller");
        ensureDemoAdmin();
        ensureDemoSeller();
        ensureCatalogCategories();
        ensureDemoProducts();
        log.info("System demo data has been initialized.");
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.findByCode(code).isPresent()) {
            return;
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        roleRepository.save(role);
    }

    private void ensureDemoAdmin() {
        if (userRepository.existsByUsername("admin")) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@stylehub.local");
        admin.setFullName("StyleHub Administrator");
        admin.setPassword(passwordEncoder.encode("Admin@123"));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRoles(java.util.Set.of(roleRepository.findByCode("ADMIN").orElseThrow()));
        userRepository.save(admin);
    }

    private void ensureDemoSeller() {
        if (userRepository.existsByUsername("seller")) {
            return;
        }

        User seller = new User();
        seller.setUsername("seller");
        seller.setEmail("seller@stylehub.local");
        seller.setFullName("StyleHub Seller");
        seller.setPassword(passwordEncoder.encode("Seller@123"));
        seller.setStatus(UserStatus.ACTIVE);
        seller.setRoles(java.util.Set.of(roleRepository.findByCode("SELLER").orElseThrow()));
        userRepository.save(seller);
    }

    private void ensureDemoProducts() {
        if (productRepository.count() > 0) {
            return;
        }

        User seller = userRepository.findByUsername("seller").orElseThrow();

        saveProduct("Classic White T-Shirt",
                "A clean everyday staple with soft cotton fabric and a versatile regular fit.",
                ProductCategory.T_SHIRTS,
                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=1200&h=1400&fit=crop",
                new BigDecimal("29.99"),
                true,
                seller);
        saveProduct("Structured Denim Jacket",
                "Layer-friendly denim jacket with polished tailoring for a sharp casual look.",
                ProductCategory.JACKETS,
                "https://images.unsplash.com/photo-1576995853123-5a10305d93c0?w=1200&h=1400&fit=crop",
                new BigDecimal("89.99"),
                true,
                seller);
        saveProduct("Summer Midi Dress",
                "Lightweight flowing dress designed for warm days and easy styling.",
                ProductCategory.DRESSES,
                "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=1200&h=1400&fit=crop",
                new BigDecimal("59.99"),
                true,
                seller);
        saveProduct("Minimal Leather Bag",
                "Compact statement accessory with premium texture and neutral tone.",
                ProductCategory.ACCESSORIES,
                "https://images.unsplash.com/photo-1590874103328-eac38a683ce7?w=1200&h=1400&fit=crop",
                new BigDecimal("129.99"),
                false,
                seller);
        saveProduct("City Walk Sneakers",
                "Comfort-first sneakers with a streamlined silhouette for all-day wear.",
                ProductCategory.SHOES,
                "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=1200&h=1400&fit=crop",
                new BigDecimal("79.99"),
                false,
                seller);
    }

    private void saveProduct(String name,
                             String description,
                             ProductCategory category,
                             String imageUrl,
                             BigDecimal price,
                             boolean featured,
                             User seller) {
        CategoryEntity categoryRef = categoryRepository.findBySlug(toCategorySlug(category))
                .orElseThrow();

        Product product = new Product();
        product.setSeller(seller);
        product.setCategoryRef(categoryRef);
        product.setTitle(name);
        product.setName(name);
        product.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        product.setDescription(description);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        product.setPrice(price);
        product.setFeatured(featured);
        product.setActive(true);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCurrency("VND");
        Product savedProduct = productRepository.save(product);
        ensureDefaultVariant(savedProduct);
    }

    private void ensureCatalogCategories() {
        for (ProductCategory category : ProductCategory.values()) {
            String slug = toCategorySlug(category);
            if (categoryRepository.findBySlug(slug).isPresent()) {
                continue;
            }

            CategoryEntity categoryEntity = new CategoryEntity();
            categoryEntity.setName(category.getDisplayName());
            categoryEntity.setSlug(slug);
            categoryEntity.setDescription("Legacy catalog category for " + category.getDisplayName());
            categoryRepository.save(categoryEntity);
        }
    }

    private String toCategorySlug(ProductCategory category) {
        return category.name().toLowerCase().replace('_', '-');
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

    private boolean hasExistingData() {
        return roleRepository.count() > 0
                || userRepository.count() > 0
                || categoryRepository.count() > 0
                || productRepository.count() > 0
                || productVariantRepository.count() > 0;
    }
}
