package com.web.shoppingweb.controller;

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
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
@RequestMapping
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        UserResponseDTO user = userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication));
        model.addAttribute("profileUser", user);
        return "profile";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        UserResponseDTO user = userService.getCurrentUser(username);

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

        model.addAttribute("dashboardUser", user);
        return "dashboard";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("updateProfileForm") UpdateProfileDTO dto,
                                BindingResult bindingResult,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.updateProfileForm", bindingResult);
            redirectAttributes.addFlashAttribute("updateProfileForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct your profile details.");
            return "redirect:/dashboard";
        }

        String username = SecurityUtils.requireCurrentUsername(authentication);
        try {
            userService.updateProfile(username, dto);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("updateProfileForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/password")
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordDTO dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.changePasswordForm", bindingResult);
            redirectAttributes.addFlashAttribute("changePasswordForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the password form.");
            return "redirect:/dashboard";
        }

        String username = SecurityUtils.requireCurrentUsername(authentication);
        try {
            userService.changePassword(username, dto);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully. Please sign in again.");
            return "redirect:/auth/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("changePasswordForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard";
        }
    }

    @PostMapping("/account/delete")
    public String deleteAccount(@Valid @ModelAttribute("deleteAccountForm") DeleteAccountDTO dto,
                                BindingResult bindingResult,
                                Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.deleteAccountForm", bindingResult);
            redirectAttributes.addFlashAttribute("deleteAccountForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", "Password confirmation is required.");
            return "redirect:/dashboard";
        }

        String username = SecurityUtils.requireCurrentUsername(authentication);
        try {
            userService.deleteAccount(username, dto.getPassword());
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Account deactivated successfully.");
            return "redirect:/auth/login";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("deleteAccountForm", dto);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard";
        }
    }
}
