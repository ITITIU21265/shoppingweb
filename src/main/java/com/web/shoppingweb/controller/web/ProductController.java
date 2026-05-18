package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final int CATALOG_PAGE_SIZE = 18;

    private final ProductService productService;
    private final ProductRecommendationService recommendationService;

    public ProductController(ProductService productService, ProductRecommendationService recommendationService) {
        this.productService = productService;
        this.recommendationService = recommendationService;
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

        return "catalog";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = productService.getProductDetail(slug);
        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", recommendationService.getRelatedProducts(product.getId(), 4));
        return "product-detail";
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
