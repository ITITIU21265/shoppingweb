package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.SavedService;

@Controller
@RequestMapping("/saved")
@PreAuthorize("isAuthenticated()")
public class SavedController {

    private final SavedService savedService;

    public SavedController(SavedService savedService) {
        this.savedService = savedService;
    }

    @GetMapping
    public String savedItems(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        model.addAttribute("savedProducts", savedService.getSavedProducts(username));
        return "saved";
    }

    @PostMapping("/{productId}/toggle")
    public String toggleSaved(@PathVariable Long productId,
                              @RequestParam(defaultValue = "/saved") String redirectTo,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        boolean saved = savedService.toggleSavedProduct(username, productId);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                saved ? "Product saved successfully." : "Product removed from saved items."
        );
        return "redirect:" + redirectTo;
    }
}
