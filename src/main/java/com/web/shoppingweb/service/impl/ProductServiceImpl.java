package com.web.shoppingweb.service.impl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final int MAX_VARIANTS = 100;
    private static final BigDecimal MAX_VND_AMOUNT = new BigDecimal("9999999999");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
    public Page<Product> getCatalogPage(ProductCategory category, String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        boolean hasKeyword = normalizedKeyword != null && !normalizedKeyword.isEmpty();

        if (category == null && !hasKeyword) {
            return productRepository.findByActiveTrueOrderByFeaturedDescCreatedAtDesc(pageable);
        }
        if (category == null) {
            return productRepository.findByActiveTrueAndNameContainingIgnoreCaseOrderByFeaturedDescCreatedAtDesc(
                    normalizedKeyword,
                    pageable
            );
        }
        if (!hasKeyword) {
            return productRepository.findByActiveTrueAndCategoryOrderByFeaturedDescCreatedAtDesc(category, pageable);
        }
        return productRepository.findByActiveTrueAndCategoryAndNameContainingIgnoreCaseOrderByFeaturedDescCreatedAtDesc(
                category,
                normalizedKeyword,
                pageable
        );
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
        List<ProductFormDTO.VariantDTO> variants = requireCreateVariants(dto.getVariants());
        BigDecimal productPrice = validatePrice(dto.getPrice());
        validateSubmittedVariants(variants);
        validateDefaultVariantPrice(variants, productPrice);

        User seller = userRepository.findByUsername(sellerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));
        ProductCategory category = ProductCategory.fromValue(dto.getCategory());

        Product product = new Product();
        product.setSeller(seller);
        synchronizeCategory(product, category);
        product.setName(dto.getName().trim());
        product.setTitle(dto.getName().trim());
        product.setDescription(dto.getDescription().trim());
        product.setPrice(productPrice);
        product.setImageUrl(dto.getImageUrl().trim());
        product.setFeatured(false);
        product.setActive(true);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCurrency("VND");
        product.setSlug(generateUniqueSlug(dto.getName()));

        Product savedProduct = productRepository.save(product);
        List<ProductVariant> savedVariants = new ArrayList<>(variants.size());
        for (int index = 0; index < variants.size(); index++) {
            ProductFormDTO.VariantDTO row = variants.get(index);
            savedVariants.add(createVariant(savedProduct, row, index + 1, index == 0));
        }
        productVariantRepository.saveAll(savedVariants);
        return savedProduct;
    }

    @Override
    public Product updateProduct(Long productId, ProductFormDTO dto, String username) {
        User actor = getUser(username);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        assertProductOwnerOrAdmin(product, actor);

        BigDecimal productPrice = validatePrice(dto.getPrice());
        List<ProductFormDTO.VariantDTO> submittedVariants = dto.getVariants();
        boolean hasVariantUpdates = submittedVariants != null && !submittedVariants.isEmpty();
        boolean priceChanged = product.getPrice() == null || product.getPrice().compareTo(productPrice) != 0;
        List<ProductVariant> existingVariants = List.of();
        if (submittedVariants != null && submittedVariants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("A product cannot have more than 100 variants.");
        }
        if (hasVariantUpdates) {
            validateSubmittedVariants(submittedVariants);
            validateDefaultVariantPrice(submittedVariants, productPrice);
            existingVariants = productVariantRepository.findAllByProduct_IdOrderByIdAsc(productId);
            validateVariantUpdateOwnershipAndOptions(existingVariants, submittedVariants);
        } else if (priceChanged) {
            existingVariants = productVariantRepository.findAllByProduct_IdOrderByIdAsc(productId);
            if (existingVariants.stream().noneMatch(ProductVariant::isDefault)) {
                throw new IllegalArgumentException(
                        "The product has no default variant to receive the updated base price.");
            }
        }

        ProductCategory category = ProductCategory.fromValue(dto.getCategory());

        synchronizeCategory(product, category);
        product.setName(dto.getName().trim());
        product.setTitle(dto.getName().trim());
        product.setDescription(dto.getDescription().trim());
        product.setPrice(productPrice);
        product.setImageUrl(dto.getImageUrl().trim());
        product.setStatus(ProductStatus.ACTIVE);
        product.setActive(true);

        if (hasVariantUpdates) {
            upsertVariants(product, existingVariants, submittedVariants);
        } else if (priceChanged) {
            synchronizeDefaultVariantPrice(existingVariants, productPrice);
        }
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

    private BigDecimal validatePrice(BigDecimal price) {
        validateWholeVndAmount(price, "Price");
        return price;
    }

    private void synchronizeCategory(Product product, ProductCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Category is required.");
        }
        CategoryEntity categoryRef = resolveCategoryRef(category);
        product.setCategory(category);
        product.setCategoryRef(categoryRef);
    }

    private CategoryEntity resolveCategoryRef(ProductCategory category) {
        String slug = category.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found for slug: " + slug));
    }

    private ProductVariant createVariant(Product product,
                                         ProductFormDTO.VariantDTO row,
                                         int skuOrdinal,
                                         boolean defaultVariant) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(formatSku(product.getId(), skuOrdinal));
        copyVariantFields(variant, row);
        variant.setDefault(defaultVariant);
        variant.setStatus(ProductVariantStatus.ACTIVE);
        return variant;
    }

    private List<ProductFormDTO.VariantDTO> requireCreateVariants(List<ProductFormDTO.VariantDTO> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("At least one product variant is required.");
        }
        if (variants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("A product cannot have more than 100 variants.");
        }
        if (variants.stream().anyMatch(row -> row != null && row.getId() != null)) {
            throw new IllegalArgumentException("New product variants must not include variant IDs.");
        }
        return variants;
    }

    private void validateSubmittedVariants(List<ProductFormDTO.VariantDTO> variants) {
        if (variants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("A product cannot have more than 100 variants.");
        }

        Set<Long> submittedIds = new HashSet<>();
        Set<VariantOptionKey> submittedOptions = new HashSet<>();
        Set<Long> sizeIds = new LinkedHashSet<>();
        Set<Long> colorIds = new LinkedHashSet<>();

        for (int index = 0; index < variants.size(); index++) {
            ProductFormDTO.VariantDTO row = variants.get(index);
            int rowNumber = index + 1;
            if (row == null) {
                throw new IllegalArgumentException("Variant row " + rowNumber + " is required.");
            }
            if (row.getId() != null && (row.getId() <= 0 || !submittedIds.add(row.getId()))) {
                throw new IllegalArgumentException("Variant row " + rowNumber + " has an invalid or duplicate ID.");
            }
            if (row.getSizeId() != null && row.getSizeId() <= 0) {
                throw new IllegalArgumentException("Variant row " + rowNumber + " has an invalid size ID.");
            }
            if (row.getColorId() != null && row.getColorId() <= 0) {
                throw new IllegalArgumentException("Variant row " + rowNumber + " has an invalid color ID.");
            }
            validateWholeVndAmount(row.getPrice(), "Variant row " + rowNumber + " price");
            if (row.getStockQty() == null || row.getStockQty() < 0) {
                throw new IllegalArgumentException(
                        "Variant row " + rowNumber + " stock quantity must be zero or greater.");
            }

            VariantOptionKey optionKey = new VariantOptionKey(row.getSizeId(), row.getColorId());
            if (!submittedOptions.add(optionKey)) {
                throw new IllegalArgumentException("Duplicate size and color combinations are not allowed.");
            }
            if (row.getSizeId() != null) {
                sizeIds.add(row.getSizeId());
            }
            if (row.getColorId() != null) {
                colorIds.add(row.getColorId());
            }
        }

        if (!sizeIds.isEmpty()) {
            validateOptionReferences("sizes", "size", sizeIds);
        }
        if (!colorIds.isEmpty()) {
            validateOptionReferences("colors", "color", colorIds);
        }
    }

    private void validateDefaultVariantPrice(List<ProductFormDTO.VariantDTO> variants, BigDecimal productPrice) {
        if (variants.get(0).getPrice().compareTo(productPrice) != 0) {
            throw new IllegalArgumentException("The first variant price must match the product price.");
        }
    }

    private void validateWholeVndAmount(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be at least 1 VND.");
        }
        if (amount.compareTo(MAX_VND_AMOUNT) > 0) {
            throw new IllegalArgumentException(fieldName + " must contain at most 10 whole-number digits.");
        }
        try {
            amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(fieldName + " must be a whole VND amount.", ex);
        }
    }

    private void validateOptionReferences(String tableName, String optionName, Set<Long> requestedIds) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
                        "select id from " + tableName + " where id in (:ids)")
                .setParameter("ids", requestedIds)
                .getResultList();
        Set<Long> foundIds = new HashSet<>();
        for (Object row : rows) {
            foundIds.add(((Number) row).longValue());
        }
        if (!foundIds.containsAll(requestedIds)) {
            Set<Long> missingIds = new LinkedHashSet<>(requestedIds);
            missingIds.removeAll(foundIds);
            throw new IllegalArgumentException("Unknown " + optionName + " ID(s): " + missingIds + ".");
        }
    }

    private void validateVariantUpdateOwnershipAndOptions(
            List<ProductVariant> existingVariants,
            List<ProductFormDTO.VariantDTO> submittedVariants) {
        Map<Long, ProductVariant> existingById = new HashMap<>();
        for (ProductVariant variant : existingVariants) {
            existingById.put(variant.getId(), variant);
        }

        Map<Long, ProductFormDTO.VariantDTO> submittedById = new HashMap<>();
        for (ProductFormDTO.VariantDTO row : submittedVariants) {
            if (row.getId() != null) {
                if (!existingById.containsKey(row.getId())) {
                    throw new IllegalArgumentException(
                            "Variant ID " + row.getId() + " does not belong to this product.");
                }
                submittedById.put(row.getId(), row);
            }
        }
        ProductFormDTO.VariantDTO requestedDefault = submittedVariants.get(0);
        if (requestedDefault.getId() != null
                && existingById.get(requestedDefault.getId()).getStatus() != ProductVariantStatus.ACTIVE) {
            throw new IllegalArgumentException("An inactive variant cannot be selected as the default variant.");
        }

        Set<VariantOptionKey> finalOptions = new HashSet<>();
        for (ProductVariant existing : existingVariants) {
            ProductFormDTO.VariantDTO replacement = submittedById.get(existing.getId());
            Long sizeId = replacement == null ? existing.getSizeId() : replacement.getSizeId();
            Long colorId = replacement == null ? existing.getColorId() : replacement.getColorId();
            if (!finalOptions.add(new VariantOptionKey(sizeId, colorId))) {
                throw new IllegalArgumentException("Duplicate size and color combinations are not allowed.");
            }
        }
        for (ProductFormDTO.VariantDTO row : submittedVariants) {
            if (row.getId() == null
                    && !finalOptions.add(new VariantOptionKey(row.getSizeId(), row.getColorId()))) {
                throw new IllegalArgumentException("Duplicate size and color combinations are not allowed.");
            }
        }
    }

    private void upsertVariants(Product product,
                                List<ProductVariant> existingVariants,
                                List<ProductFormDTO.VariantDTO> submittedVariants) {
        Map<Long, ProductVariant> existingById = new HashMap<>();
        Set<String> usedSkus = new HashSet<>();
        for (ProductVariant variant : existingVariants) {
            existingById.put(variant.getId(), variant);
            usedSkus.add(variant.getSku());
            variant.setDefault(false);
        }

        List<ProductVariant> variantsToSave = new ArrayList<>(existingVariants);
        int nextSkuOrdinal = existingVariants.size() + 1;
        for (int index = 0; index < submittedVariants.size(); index++) {
            ProductFormDTO.VariantDTO row = submittedVariants.get(index);
            ProductVariant variant;
            if (row.getId() == null) {
                while (usedSkus.contains(formatSku(product.getId(), nextSkuOrdinal))) {
                    nextSkuOrdinal++;
                }
                variant = createVariant(product, row, nextSkuOrdinal++, index == 0);
                usedSkus.add(variant.getSku());
                variantsToSave.add(variant);
            } else {
                variant = existingById.get(row.getId());
                copyVariantFields(variant, row);
                variant.setDefault(index == 0);
            }
        }
        productVariantRepository.saveAll(variantsToSave);
    }

    private void copyVariantFields(ProductVariant variant, ProductFormDTO.VariantDTO row) {
        variant.setSizeId(row.getSizeId());
        variant.setColorId(row.getColorId());
        variant.setPriceAmount(row.getPrice().longValueExact());
        variant.setStockQty(row.getStockQty());
    }

    private void synchronizeDefaultVariantPrice(List<ProductVariant> existingVariants, BigDecimal productPrice) {
        ProductVariant defaultVariant = existingVariants.stream()
                .filter(ProductVariant::isDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The product has no default variant to receive the updated base price."));
        defaultVariant.setPriceAmount(productPrice.longValueExact());
        productVariantRepository.saveAll(List.of(defaultVariant));
    }

    private String formatSku(Long productId, int ordinal) {
        return "P" + productId + "-V" + String.format(Locale.ROOT, "%03d", ordinal);
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

    private record VariantOptionKey(Long sizeId, Long colorId) {
    }
}
