package com.web.shoppingweb.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.product.ProductResponseDTO;
import com.web.shoppingweb.dto.saved.SavedItemRequestDTO;
import com.web.shoppingweb.dto.saved.SavedToggleResponseDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.SavedService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/saved")
public class SavedApiController {

    private final SavedService savedService;

    public SavedApiController(SavedService savedService) {
        this.savedService = savedService;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getSavedItems(Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        List<ProductResponseDTO> response = savedService.getSavedProducts(username).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SavedToggleResponseDTO> toggleSaved(@Valid @RequestBody SavedItemRequestDTO request,
                                                              Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        boolean saved = savedService.toggleSavedProduct(username, request.getProductId());
        return ResponseEntity.ok(new SavedToggleResponseDTO(saved, savedService.countSavedItems(username)));
    }

    @DeleteMapping
    public ResponseEntity<SavedToggleResponseDTO> removeSaved(@Valid @RequestBody SavedItemRequestDTO request,
                                                              Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        savedService.removeSavedProduct(username, request.getProductId());
        return ResponseEntity.ok(new SavedToggleResponseDTO(false, savedService.countSavedItems(username)));
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
