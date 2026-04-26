package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.supplier.SupplierRegistrationDTO;
import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.SupplierService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/supplier")
@PreAuthorize("hasRole('CUSTOMER')")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        Supplier supplier = supplierService.findCurrentUserSupplier(username).orElse(null);
        boolean pendingReview = supplier != null && supplier.getStatus() == SupplierStatus.PENDING;

        if (!model.containsAttribute("supplierRegistrationForm")) {
            SupplierRegistrationDTO dto = new SupplierRegistrationDTO();
            if (supplier != null) {
                dto.setName(supplier.getName());
                dto.setPhone(supplier.getPhone());
                dto.setEmail(supplier.getEmail());
                dto.setAddress(supplier.getAddress());
            }
            model.addAttribute("supplierRegistrationForm", dto);
        }

        model.addAttribute("supplier", supplier);
        model.addAttribute("pendingReview", pendingReview);
        return "supplier-registration";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("supplierRegistrationForm") SupplierRegistrationDTO dto,
                           BindingResult bindingResult,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.supplierRegistrationForm",
                    bindingResult);
            redirectAttributes.addFlashAttribute("supplierRegistrationForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the seller registration form.");
            return "redirect:/supplier/register";
        }

        String username = SecurityUtils.requireCurrentUsername(authentication);
        supplierService.register(username, dto);
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Your store application has been submitted successfully.");
        return "redirect:/supplier/register";
    }
}
