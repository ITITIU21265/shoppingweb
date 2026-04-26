package com.web.shoppingweb.controller.web;

import java.util.Locale;
import java.util.Set;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.auth.ChangePasswordDTO;
import com.web.shoppingweb.dto.user.DeleteAccountDTO;
import com.web.shoppingweb.dto.user.UpdateProfileDTO;
import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping
@PreAuthorize("isAuthenticated()")
public class UserController {

    private static final Set<String> ADMIN_DASHBOARD_VIEWS =
            Set.of("overview", "orders", "products", "customers", "marketing", "audit", "users", "settings");
    private static final Set<String> SELLER_DASHBOARD_VIEWS =
            Set.of("overview", "orders", "products", "marketing", "settings");
    private static final Set<String> CUSTOMER_DASHBOARD_VIEWS =
            Set.of("overview", "orders", "saved", "settings");

    private final UserService userService;
    private final AdminDashboardModelBuilder adminDashboardModelBuilder;
    private final SellerDashboardModelBuilder sellerDashboardModelBuilder;
    private final CustomerDashboardModelBuilder customerDashboardModelBuilder;

    public UserController(UserService userService,
                          AdminDashboardModelBuilder adminDashboardModelBuilder,
                          SellerDashboardModelBuilder sellerDashboardModelBuilder,
                          CustomerDashboardModelBuilder customerDashboardModelBuilder) {
        this.userService = userService;
        this.adminDashboardModelBuilder = adminDashboardModelBuilder;
        this.sellerDashboardModelBuilder = sellerDashboardModelBuilder;
        this.customerDashboardModelBuilder = customerDashboardModelBuilder;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        UserResponseDTO user = userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication));
        if (hasDashboardAccess(user)) {
            return "redirect:/dashboard?view=settings";
        }
        populateAccountForms(model, user);
        model.addAttribute("profileUser", user);
        return "profile";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER','CUSTOMER')")
    public String dashboard(@RequestParam(defaultValue = "overview") String view,
                            @RequestParam(defaultValue = "all") String segment,
                            @RequestParam(required = false) Long customerId,
                            @RequestParam(required = false) Long orderId,
                            @RequestParam(required = false) String userQuery,
                            @RequestParam(defaultValue = "all") String userRoleFilter,
                            @RequestParam(defaultValue = "all") String userStatusFilter,
                            @RequestParam(defaultValue = "1") int userPage,
                            Authentication authentication,
                            Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);
        String activeView = resolveDashboardView(user, view);

        populateAccountForms(model, user);
        model.addAttribute("dashboardUser", user);
        model.addAttribute("activeDashboardView", activeView);
        model.addAttribute("isAdminDashboard", "ADMIN".equalsIgnoreCase(user.getRole()));
        model.addAttribute("isSellerDashboard", "SELLER".equalsIgnoreCase(user.getRole()));
        model.addAttribute("isCustomerDashboard", "CUSTOMER".equalsIgnoreCase(user.getRole()));
        populateRoleDashboard(
                model,
                user,
                new DashboardRequest(
                        activeView,
                        segment,
                        customerId,
                        orderId,
                        userQuery,
                        userRoleFilter,
                        userStatusFilter,
                        userPage
                )
        );
        return "dashboard";
    }

    @PostMapping("/dashboard/marketing/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public String previewPlatformCoupon(@RequestParam String code,
                                        @RequestParam(required = false) String type,
                                        RedirectAttributes redirectAttributes) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Coupon code is required.");
            return "redirect:/dashboard?view=marketing";
        }

        String normalizedType = (type == null || type.isBlank()) ? "platform_coupon" : type;
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Coupon draft " + normalizedCode + " captured for " + normalizedType
                        + ". Persistent coupon storage is not wired yet in the current schema."
        );
        return "redirect:/dashboard?view=marketing";
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
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the profile form.");
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

    @PostMapping("/account/password")
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordDTO dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
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
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("changePasswordForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return accountHome;
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

    private void populateRoleDashboard(Model model, UserResponseDTO user, DashboardRequest request) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            adminDashboardModelBuilder.populate(model, request);
            return;
        }

        if ("SELLER".equalsIgnoreCase(user.getRole())) {
            sellerDashboardModelBuilder.populate(model, user.getUsername());
            return;
        }

        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
            customerDashboardModelBuilder.populate(model, user.getUsername());
        }
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

    private String resolveAccountHome(UserResponseDTO user) {
        return hasDashboardAccess(user) ? "redirect:/dashboard?view=settings" : "redirect:/profile";
    }

    private boolean hasDashboardAccess(UserResponseDTO user) {
        return user != null
                && ("ADMIN".equalsIgnoreCase(user.getRole())
                || "SELLER".equalsIgnoreCase(user.getRole())
                || "CUSTOMER".equalsIgnoreCase(user.getRole()));
    }

    private String resolveDashboardView(UserResponseDTO user, String requestedView) {
        if (user == null) {
            return "overview";
        }

        Set<String> allowedViews;
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            allowedViews = ADMIN_DASHBOARD_VIEWS;
        } else if ("SELLER".equalsIgnoreCase(user.getRole())) {
            allowedViews = SELLER_DASHBOARD_VIEWS;
        } else {
            allowedViews = CUSTOMER_DASHBOARD_VIEWS;
        }

        if (requestedView == null || requestedView.isBlank()) {
            return "overview";
        }

        String normalizedView = requestedView.trim().toLowerCase(Locale.ROOT);
        return allowedViews.contains(normalizedView) ? normalizedView : "overview";
    }
}
