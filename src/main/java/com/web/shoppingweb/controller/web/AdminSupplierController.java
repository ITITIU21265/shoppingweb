package com.web.shoppingweb.controller.web;

import java.util.HashSet;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.entity.user.Role;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.supplier.SupplierRepository;
import com.web.shoppingweb.repository.user.RefreshTokenRepository;
import com.web.shoppingweb.repository.user.RoleRepository;
import com.web.shoppingweb.repository.user.UserRepository;

@Controller
@RequestMapping("/admin/suppliers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupplierController {

    private static final String USER_MANAGEMENT_REDIRECT = "redirect:/dashboard?view=users";

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminSupplierController(SupplierRepository supplierRepository,
                                   UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   RefreshTokenRepository refreshTokenRepository) {
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @GetMapping("/review/{userId}")
    public String review(@PathVariable Long userId, Model model) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Supplier supplier = supplierRepository.findFirstBySeller_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));

        model.addAttribute("reviewUser", user);
        model.addAttribute("supplier", supplier);
        return "admin-supplier-review";
    }

    @PostMapping("/approve/{supplierId}")
    @Transactional
    public String approve(@PathVariable Long supplierId, RedirectAttributes redirectAttributes) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));
        Long userId = supplier.getSeller() == null ? null : supplier.getSeller().getId();
        if (userId == null) {
            throw new ResourceNotFoundException("Supplier application is not linked to a user");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        supplier.setStatus(SupplierStatus.APPROVED);
        supplierRepository.save(supplier);

        Role sellerRole = findRole("SELLER");
        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(sellerRole);
        userRepository.save(user);
        refreshTokenRepository.deleteAllByUserValue(user);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Supplier application approved. The user must sign in again to receive seller access.");
        return USER_MANAGEMENT_REDIRECT;
    }

    @PostMapping("/reject/{supplierId}")
    @Transactional
    public String reject(@PathVariable Long supplierId, RedirectAttributes redirectAttributes) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));
        supplier.setStatus(SupplierStatus.REJECTED);
        supplierRepository.save(supplier);

        redirectAttributes.addFlashAttribute("successMessage", "Supplier application rejected.");
        return USER_MANAGEMENT_REDIRECT;
    }

    private Role findRole(String roleCode) {
        String normalizedCode = roleCode.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(normalizedCode)
                .or(() -> roleRepository.findByCode("ROLE_" + normalizedCode))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + normalizedCode));
    }
}
