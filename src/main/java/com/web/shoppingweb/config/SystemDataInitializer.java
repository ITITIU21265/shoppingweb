package com.web.shoppingweb.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductCategory;
import com.web.shoppingweb.entity.Role;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.entity.UserStatus;
import com.web.shoppingweb.repository.ProductRepository;
import com.web.shoppingweb.repository.RoleRepository;
import com.web.shoppingweb.repository.UserRepository;

@Component
public class SystemDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public SystemDataInitializer(RoleRepository roleRepository,
                                 UserRepository userRepository,
                                 ProductRepository productRepository,
                                 PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        ensureRole("ADMIN", "Administrator");
        ensureRole("CUSTOMER", "Customer");
        ensureRole("SELLER", "Seller");
        ensureDemoAdmin();
        ensureDemoProducts();
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

    private void ensureDemoProducts() {
        if (productRepository.count() > 0) {
            return;
        }

        saveProduct("Classic White T-Shirt",
                "A clean everyday staple with soft cotton fabric and a versatile regular fit.",
                ProductCategory.T_SHIRTS,
                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=1200&h=1400&fit=crop",
                new BigDecimal("29.99"),
                true);
        saveProduct("Structured Denim Jacket",
                "Layer-friendly denim jacket with polished tailoring for a sharp casual look.",
                ProductCategory.JACKETS,
                "https://images.unsplash.com/photo-1576995853123-5a10305d93c0?w=1200&h=1400&fit=crop",
                new BigDecimal("89.99"),
                true);
        saveProduct("Summer Midi Dress",
                "Lightweight flowing dress designed for warm days and easy styling.",
                ProductCategory.DRESSES,
                "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=1200&h=1400&fit=crop",
                new BigDecimal("59.99"),
                true);
        saveProduct("Minimal Leather Bag",
                "Compact statement accessory with premium texture and neutral tone.",
                ProductCategory.ACCESSORIES,
                "https://images.unsplash.com/photo-1590874103328-eac38a683ce7?w=1200&h=1400&fit=crop",
                new BigDecimal("129.99"),
                false);
        saveProduct("City Walk Sneakers",
                "Comfort-first sneakers with a streamlined silhouette for all-day wear.",
                ProductCategory.SHOES,
                "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=1200&h=1400&fit=crop",
                new BigDecimal("79.99"),
                false);
    }

    private void saveProduct(String name,
                             String description,
                             ProductCategory category,
                             String imageUrl,
                             BigDecimal price,
                             boolean featured) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        product.setDescription(description);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        product.setPrice(price);
        product.setFeatured(featured);
        product.setActive(true);
        productRepository.save(product);
    }
}
