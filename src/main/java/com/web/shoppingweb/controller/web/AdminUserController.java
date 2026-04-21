package com.web.shoppingweb.controller.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.UpdateRoleDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.service.UserService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String getAllUsers(Model model) {
        List<UserResponseDTO> users = userService.getAllUsers();
        model.addAttribute("users", users);
        if (!model.containsAttribute("roleForm")) {
            model.addAttribute("roleForm", new UpdateRoleDTO());
        }
        return "admin-users";
    }

    @PostMapping("/{id}/role")
    public String updateUserRole(@PathVariable Long id,
                                 @Valid @ModelAttribute("roleForm") UpdateRoleDTO dto,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.updateUserRole(id, dto);
            redirectAttributes.addFlashAttribute("successMessage", "User role updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "User status updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
