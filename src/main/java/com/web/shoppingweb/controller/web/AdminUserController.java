package com.web.shoppingweb.controller.web;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.web.shoppingweb.dto.user.UpdateRoleDTO;
import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.service.UserService;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final String USER_MANAGEMENT_REDIRECT = "redirect:/dashboard?view=users";
    private static final Set<String> MANAGED_ROLE_CODES = Set.of("CUSTOMER", "SELLER");

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String getAllUsers() {
        return redirectToUserManagement(null, "all", "all", 1);
    }

    @PostMapping("/{id}/role")
    public String updateUserRole(@PathVariable Long id,
                                 @RequestParam("roleCode") String roleCode,
                                 @RequestParam(required = false) String userQuery,
                                 @RequestParam(defaultValue = "all") String userRoleFilter,
                                 @RequestParam(defaultValue = "all") String userStatusFilter,
                                 @RequestParam(defaultValue = "1") int userPage,
                                 RedirectAttributes redirectAttributes) {
        String targetRole = roleCode == null
                ? ""
                : roleCode.trim().toUpperCase(Locale.ROOT);

        if (!MANAGED_ROLE_CODES.contains(targetRole)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only CUSTOMER and SELLER roles can be assigned from this console.");
            return redirectToUserManagement(userQuery, userRoleFilter, userStatusFilter, userPage);
        }

        if (!canManageTarget(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Admin accounts are managed outside this workspace.");
            return redirectToUserManagement(userQuery, userRoleFilter, userStatusFilter, userPage);
        }

        try {
            UpdateRoleDTO dto = new UpdateRoleDTO();
            dto.setRoleCode(targetRole);
            userService.updateUserRole(id, dto);
            redirectAttributes.addFlashAttribute("successMessage", "User role updated successfully. The affected user should sign in again to refresh permissions.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToUserManagement(userQuery, userRoleFilter, userStatusFilter, userPage);
    }

    @PostMapping("/{id}/status")
    public String toggleUserStatus(@PathVariable Long id,
                                   @RequestParam(required = false) String userQuery,
                                   @RequestParam(defaultValue = "all") String userRoleFilter,
                                   @RequestParam(defaultValue = "all") String userStatusFilter,
                                   @RequestParam(defaultValue = "1") int userPage,
                                   RedirectAttributes redirectAttributes) {
        if (!canManageTarget(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Admin accounts are managed outside this workspace.");
            return redirectToUserManagement(userQuery, userRoleFilter, userStatusFilter, userPage);
        }

        try {
            userService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "User status updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirectToUserManagement(userQuery, userRoleFilter, userStatusFilter, userPage);
    }

    private String redirectToUserManagement(String userQuery,
                                            String userRoleFilter,
                                            String userStatusFilter,
                                            int userPage) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/dashboard")
                .queryParam("view", "users")
                .queryParam("userRoleFilter", userRoleFilter == null || userRoleFilter.isBlank() ? "all" : userRoleFilter)
                .queryParam("userStatusFilter", userStatusFilter == null || userStatusFilter.isBlank() ? "all" : userStatusFilter)
                .queryParam("userPage", Math.max(userPage, 1));

        if (userQuery != null && !userQuery.isBlank()) {
            builder.queryParam("userQuery", userQuery.trim());
        }

        return "redirect:" + builder.build().toUriString();
    }

    private boolean canManageTarget(Long id) {
        List<UserResponseDTO> users = userService.getAllUsers();
        return users.stream()
                .filter(user -> Objects.equals(user.getId(), id))
                .findFirst()
                .map(user -> "CUSTOMER".equalsIgnoreCase(user.getRole()) || "SELLER".equalsIgnoreCase(user.getRole()))
                .orElse(false);
    }
}
