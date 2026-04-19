package com.web.shoppingweb.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.ProductFormDTO;
import com.web.shoppingweb.entity.ProductCategory;
import com.web.shoppingweb.service.ProductService;

import jakarta.validation.Valid;

@Controller
@RequestMapping
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/catalog")
    public String catalog(@RequestParam(required = false) String category, Model model) {
        ProductCategory selectedCategory = ProductCategory.fromValue(category);
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("categories", productService.getAvailableCategories());
        model.addAttribute("products", productService.getCatalog(selectedCategory));

        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductFormDTO());
        }

        return "catalog";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        model.addAttribute("product", productService.getProductDetail(slug));
        return "product-detail";
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public String createProduct(@Valid @ModelAttribute("productForm") ProductFormDTO productForm,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.productForm", bindingResult);
            redirectAttributes.addFlashAttribute("productForm", productForm);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the product form.");
            return "redirect:/catalog";
        }

        try {
            productService.createProduct(productForm);
            redirectAttributes.addFlashAttribute("successMessage", "Product created successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("productForm", productForm);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/catalog";
    }
}
