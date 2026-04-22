package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.ChangePasswordDTO;
import com.web.shoppingweb.dto.DeleteAccountDTO;
import com.web.shoppingweb.dto.UpdateProfileDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.entity.Order;
import com.web.shoppingweb.entity.Product;
import com.web.shoppingweb.entity.ProductVariant;
import com.web.shoppingweb.entity.ProductVariantStatus;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.repository.OrderRepository;
import com.web.shoppingweb.repository.ProductRepository;
import com.web.shoppingweb.repository.ProductVariantRepository;
import com.web.shoppingweb.service.ProductService;
import com.web.shoppingweb.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;

    public UserController(UserService userService,
                          ProductService productService,
                          ProductRepository productRepository,
                          ProductVariantRepository productVariantRepository,
                          OrderRepository orderRepository) {
        this.userService = userService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        UserResponseDTO user = userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication));
        populateAccountForms(model, user);
        model.addAttribute("profileUser", user);
        return "profile";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public String dashboard(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);

        populateAccountForms(model, user);
        model.addAttribute("dashboardUser", user);
        populateRoleDashboard(model, user);
        return "dashboard";
    }

    private void populateAccountForms(Model model, UserResponseDTO user) {
        if (!model.containsAttribute("updateProfileForm")) {
            UpdateProfileDTO updateProfileDTO = new UpdateProfileDTO();
            updateProfileDTO.setFullName(user.getFullName());
            updateProfileDTO.setEmail(user.getEmail());
            model.addAttribute("updateProfileForm", updateProfileDTO);
        }

        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute("changePasswordForm", new ChangePasswordDTO());
        }

        if (!model.containsAttribute("deleteAccountForm")) {
            model.addAttribute("deleteAccountForm", new DeleteAccountDTO());
        }
    }

    private void populateRoleDashboard(Model model, UserResponseDTO user) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            populateAdminDashboard(model);
            return;
        }

        if ("SELLER".equalsIgnoreCase(user.getRole())) {
            populateSellerDashboard(model, user.getUsername());
        }
    }

    private void populateAdminDashboard(Model model) {
        List<UserResponseDTO> users = userService.getAllUsers();
        List<Order> recentOrders = orderRepository.findTop5ByOrderByCreatedAtDesc();
        List<Order> allOrders = orderRepository.findAll();

        long activeUsers = users.stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .count();
        long adminCount = users.stream()
                .filter(user -> "ADMIN".equalsIgnoreCase(user.getRole()))
                .count();
        long sellerCount = users.stream()
                .filter(user -> "SELLER".equalsIgnoreCase(user.getRole()))
                .count();
        long customerCount = users.stream()
                .filter(user -> "CUSTOMER".equalsIgnoreCase(user.getRole()))
                .count();
        BigDecimal totalRevenue = allOrders.stream()
                .map(Order::getSubtotalAmount)
                .filter(java.util.Objects::nonNull)
                .map(amount -> BigDecimal.valueOf(amount, 2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("adminTotalUsers", users.size());
        model.addAttribute("adminActiveUsers", activeUsers);
        model.addAttribute("adminTotalProducts", productService.countProducts());
        model.addAttribute("adminTotalOrders", allOrders.size());
        model.addAttribute("adminTotalRevenue", totalRevenue);
        model.addAttribute("adminCategoryCount", productService.getAvailableCategories().size());
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("sellerCount", sellerCount);
        model.addAttribute("customerCount", customerCount);
        model.addAttribute("recentOrders", recentOrders);
    }

    private void populateSellerDashboard(Model model, String username) {
        List<Product> sellerProducts = productRepository.findBySeller_UsernameOrderByCreatedAtDesc(username);
        List<Product> recentSellerProducts = productRepository.findTop5BySeller_UsernameOrderByCreatedAtDesc(username);
        List<ProductVariant> lowStockVariants =
                productVariantRepository.findTop5ByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatusOrderByStockQtyAscIdAsc(
                        username,
                        5,
                        ProductVariantStatus.ACTIVE
                );

        long categoryCount = sellerProducts.stream()
                .map(Product::getCategory)
                .distinct()
                .count();
        long activeProductCount = sellerProducts.stream()
                .filter(Product::isActive)
                .count();

        model.addAttribute("sellerManagedProducts", productRepository.countBySeller_Username(username));
        model.addAttribute("sellerFeaturedProducts", productRepository.countBySeller_UsernameAndFeaturedTrue(username));
        model.addAttribute(
                "sellerLowStockCount",
                productVariantRepository.countByProduct_Seller_UsernameAndStockQtyLessThanEqualAndStatus(
                        username,
                        5,
                        ProductVariantStatus.ACTIVE
                )
        );
        model.addAttribute("sellerCategoryCount", categoryCount);
        model.addAttribute("sellerActiveProducts", activeProductCount);
        model.addAttribute("recentSellerProducts", recentSellerProducts);
        model.addAttribute("sellerLowStockVariants", lowStockVariants);
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("updateProfileForm") UpdateProfileDTO dto,
                                BindingResult bindingResult,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);
        String accountHome = resolveAccountHome(user);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.updateProfileForm", bindingResult);
            redirectAttributes.addFlashAttribute("updateProfileForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct your profile details.");
            return accountHome;
        }

        try {
            userService.updateProfile(username, dto);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("updateProfileForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return accountHome;
    }

    @PostMapping({"/dashboard/password", "/profile/password"})
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordDTO dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);
        String accountHome = resolveAccountHome(user);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.changePasswordForm", bindingResult);
            redirectAttributes.addFlashAttribute("changePasswordForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the password form.");
            return accountHome;
        }

        try {
            userService.changePassword(username, dto);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully. Please sign in again.");
            return "redirect:/auth/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("changePasswordForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return accountHome;
        }
    }

    @PostMapping("/account/delete")
    public String deleteAccount(@Valid @ModelAttribute("deleteAccountForm") DeleteAccountDTO dto,
                                BindingResult bindingResult,
                                Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);
        String accountHome = resolveAccountHome(user);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.deleteAccountForm", bindingResult);
            redirectAttributes.addFlashAttribute("deleteAccountForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Password confirmation is required.");
            return accountHome;
        }

        try {
            userService.deleteAccount(username, dto.getPassword());
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Account deactivated successfully.");
            return "redirect:/auth/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("deleteAccountForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return accountHome;
        }
    }

    private String resolveAccountHome(UserResponseDTO user) {
        return hasDashboardAccess(user) ? "redirect:/dashboard" : "redirect:/profile";
    }

    private boolean hasDashboardAccess(UserResponseDTO user) {
        return user != null
                && ("ADMIN".equalsIgnoreCase(user.getRole()) || "SELLER".equalsIgnoreCase(user.getRole()));
    }
}
