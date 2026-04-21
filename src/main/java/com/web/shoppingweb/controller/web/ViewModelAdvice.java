package com.web.shoppingweb.controller.web;

import java.util.Set;
import java.util.Collections;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
// import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.web.shoppingweb.dto.ProductFormDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.ProductService;
import com.web.shoppingweb.service.SavedService;
import com.web.shoppingweb.service.UserService;

@ControllerAdvice(basePackages = "com.web.shoppingweb.controller.web")
public class ViewModelAdvice {

    private final UserService userService;
    private final ProductService productService;
    private final SavedService savedService;
    private final CartService cartService;

    public ViewModelAdvice(UserService userService,
                           ProductService productService,
                           SavedService savedService,
                           CartService cartService) {
        this.userService = userService;
        this.productService = productService;
        this.savedService = savedService;
        this.cartService = cartService;
    }

    @ModelAttribute("currentUser")
    public UserResponseDTO currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication));
    }

    @ModelAttribute("signedIn")
    public boolean signedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("canManageProducts")
    public boolean canManageProducts(Authentication authentication) {
        if (!signedIn(authentication)) {
            return false;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        return authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SELLER");
    }

    @ModelAttribute("catalogCategories")
    public Object catalogCategories() {
        return productService.getAvailableCategories();
    }

    @ModelAttribute("productForm")
    public ProductFormDTO productForm() {
        return new ProductFormDTO();
    }

    @ModelAttribute("savedItemCount")
    public long savedItemCount(Authentication authentication) {
        if (!signedIn(authentication)) {
            return 0L;
        }
        return savedService.countSavedItems(SecurityUtils.requireCurrentUsername(authentication));
    }

    @ModelAttribute("cartItemCount")
    public long cartItemCount(Authentication authentication) {
        if (!signedIn(authentication)) {
            return 0L;
        }
        return cartService.countCartItems(SecurityUtils.requireCurrentUsername(authentication));
    }

    @ModelAttribute("savedProductIds")
    public Set<Long> savedProductIds(Authentication authentication) {
        if (!signedIn(authentication)) {
            return Collections.emptySet();
        }
        return savedService.getSavedProductIds(SecurityUtils.requireCurrentUsername(authentication));
    }
}
