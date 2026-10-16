package com.web.shoppingweb.controller.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.ProductService;
import com.web.shoppingweb.service.ProductRecommendationService;

import jakarta.validation.Valid;

@Controller
@RequestMapping
public class ProductController {

    private static final int CATALOG_PAGE_SIZE = 24;

    private final ProductService productService;
    private final ProductRecommendationService recommendationService;
    private final JdbcTemplate jdbcTemplate;

    public ProductController(ProductService productService, ProductRecommendationService recommendationService,
                             JdbcTemplate jdbcTemplate) {
        this.productService = productService;
        this.recommendationService = recommendationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @InitBinder("productForm")
    void configureProductBinding(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(100);
        binder.setAllowedFields("name", "description", "price", "category", "imageUrl",
                "variants[*].id", "variants[*].sizeId", "variants[*].colorId",
                "variants[*].price", "variants[*].stockQty");
    }

    @GetMapping("/catalog")
    public String catalog(@RequestParam(required = false) String category,
                          @RequestParam(name = "q", required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          Model model) {
        ProductCategory selectedCategory = ProductCategory.fromValue(category);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        int requestedPage = Math.max(page, 1);
        Page<Product> catalogPage = productService.getCatalogPage(
                selectedCategory,
                normalizedKeyword,
                PageRequest.of(requestedPage - 1, CATALOG_PAGE_SIZE)
        );

        if (catalogPage.getTotalPages() > 0 && requestedPage > catalogPage.getTotalPages()) {
            catalogPage = productService.getCatalogPage(
                    selectedCategory,
                    normalizedKeyword,
                    PageRequest.of(catalogPage.getTotalPages() - 1, CATALOG_PAGE_SIZE)
            );
        }

        int totalPages = catalogPage.getTotalPages();
        int currentPage = totalPages == 0 ? 1 : catalogPage.getNumber() + 1;
        long totalProducts = catalogPage.getTotalElements();
        long showingFrom = totalProducts == 0 ? 0 : ((long) catalogPage.getNumber() * CATALOG_PAGE_SIZE) + 1;
        long showingTo = totalProducts == 0 ? 0 : showingFrom + catalogPage.getNumberOfElements() - 1;
        int startPage = totalPages == 0 ? 0 : Math.max(1, currentPage - 2);
        int endPage = totalPages == 0 ? 0 : Math.min(totalPages, currentPage + 2);

        if (totalPages > 0 && endPage - startPage < 4) {
            if (startPage == 1) {
                endPage = Math.min(totalPages, 5);
            } else if (endPage == totalPages) {
                startPage = Math.max(1, totalPages - 4);
            }
        }

        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("categories", productService.getAvailableCategories());
        model.addAttribute("products", catalogPage.getContent());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("showingFrom", showingFrom);
        model.addAttribute("showingTo", showingTo);
        model.addAttribute("hasPreviousPage", catalogPage.hasPrevious());
        model.addAttribute("hasNextPage", catalogPage.hasNext());
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductFormDTO());
        }
        ProductFormDTO form = (ProductFormDTO) model.getAttribute("productForm");
        if (form.getVariants() == null || form.getVariants().isEmpty()) {
            form.setVariants(new java.util.ArrayList<>());
            form.getVariants().add(new ProductFormDTO.VariantDTO());
        }
        model.addAttribute("variantSizes", jdbcTemplate.queryForList(
                "SELECT id, label, size_system FROM sizes ORDER BY size_system, id"));
        model.addAttribute("variantColors", jdbcTemplate.queryForList(
                "SELECT id, name FROM colors ORDER BY name, id"));

        return "catalog";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = productService.getProductDetail(slug);
        model.addAttribute("product", product);
        model.addAttribute("productVariants", loadProductVariants(product.getId()));
        model.addAttribute("productImages", loadProductImages(product));
        model.addAttribute("productSellerName", resolveSellerName(product));
        model.addAttribute("relatedProducts", recommendationService.getRelatedProducts(product.getId(), 4));
        return "product-detail";
    }

    private List<Map<String, Object>> loadProductVariants(Long productId) {
        return jdbcTemplate.query("""
                SELECT pv.id, pv.sku, pv.size_id, pv.color_id,
                       s.label AS size_label, s.size_system,
                       c.name AS color_name, pv.price_amount, pv.stock_qty
                FROM product_variants pv
                LEFT JOIN sizes s ON s.id = pv.size_id
                LEFT JOIN colors c ON c.id = pv.color_id
                WHERE pv.product_id = ? AND pv.status = 'ACTIVE'
                ORDER BY pv.is_default DESC, pv.id ASC
                """, (resultSet, rowNumber) -> {
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("id", resultSet.getObject("id"));
            variant.put("sku", resultSet.getString("sku"));
            variant.put("size_id", resultSet.getObject("size_id"));
            variant.put("color_id", resultSet.getObject("color_id"));
            variant.put("size_label", resultSet.getString("size_label"));
            variant.put("size_system", resultSet.getString("size_system"));
            variant.put("color_name", resultSet.getString("color_name"));
            variant.put("price_amount", resultSet.getObject("price_amount"));
            variant.put("stock_qty", resultSet.getObject("stock_qty"));
            return variant;
        }, productId);
    }

    private List<String> loadProductImages(Product product) {
        LinkedHashSet<String> images = new LinkedHashSet<>();
        addImage(images, product.getImageUrl());
        jdbcTemplate.queryForList("""
                        SELECT image_url
                        FROM product_images
                        WHERE product_id = ?
                        ORDER BY sort_order ASC, id ASC
                        """, String.class, product.getId())
                .forEach(image -> addImage(images, image));
        return new ArrayList<>(images);
    }

    private void addImage(LinkedHashSet<String> images, String imageUrl) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            images.add(imageUrl);
        }
    }

    private String resolveSellerName(Product product) {
        if (product.getSeller() == null) {
            return null;
        }
        String fullName = product.getSeller().getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String username = product.getSeller().getUsername();
        return username == null || username.isBlank() ? null : username;
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public String createProduct(@Valid @ModelAttribute("productForm") ProductFormDTO productForm,
                                BindingResult bindingResult,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.productForm", bindingResult);
            redirectAttributes.addFlashAttribute("productForm", productForm);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the product form.");
            return "redirect:/catalog";
        }

        try {
            productService.createProduct(productForm, SecurityUtils.requireCurrentUsername(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Product created successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("productForm", productForm);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/catalog";
    }
}
