package com.web.shoppingweb.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.dto.product.ProductResponseDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> catalog(@RequestParam(required = false) String category) {
        ProductCategory selectedCategory = ProductCategory.fromValue(category);
        List<ProductResponseDTO> response = productService.getCatalog(selectedCategory).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProductResponseDTO> productDetail(@PathVariable String slug) {
        return ResponseEntity.ok(toResponse(productService.getProductDetail(slug)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductFormDTO productForm,
                                                            Authentication authentication) {
        Product created = productService.createProduct(
                productForm,
                SecurityUtils.requireCurrentUsername(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductResponseDTO> updateProduct(@PathVariable Long id,
                                                            @Valid @RequestBody ProductFormDTO productForm,
                                                            Authentication authentication) {
        Product updated = productService.updateProduct(
                id,
                productForm,
                SecurityUtils.requireCurrentUsername(authentication)
        );
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id,
                                              Authentication authentication) {
        productService.deleteProduct(
                id,
                SecurityUtils.requireCurrentUsername(authentication)
        );
        return ResponseEntity.noContent().build();
    }

    private ProductResponseDTO toResponse(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory().getDisplayName(),
                product.getImageUrl(),
                product.isFeatured()
        );
    }
}
