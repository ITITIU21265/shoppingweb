package com.web.shoppingweb.controller.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.dto.ForgotPasswordDTO;
import com.web.shoppingweb.dto.RegisterRequestDTO;
import com.web.shoppingweb.dto.ResetPasswordDTO;
import com.web.shoppingweb.exception.DuplicateResourceException;
import com.web.shoppingweb.service.UserService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/login"})
    public String loginPage(Authentication authentication,
                            @RequestParam(required = false) boolean error,
                            @RequestParam(required = false) boolean logout,
                            @RequestParam(required = false) boolean registered,
                            @RequestParam(required = false) boolean inactive,
                            @RequestParam(required = false) String tab,
                            Model model) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }

        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequestDTO());
        }

        String requestedTab = "signup".equalsIgnoreCase(tab) ? "signup" : "signin";

        if (!model.containsAttribute("activeTab")) {
            model.addAttribute("activeTab", requestedTab);
        }

        if (error) {
            model.addAttribute("activeTab", "signin");
            model.addAttribute("errorMessage", "Invalid username, email, or password.");
        } else if (inactive) {
            model.addAttribute("activeTab", "signin");
            model.addAttribute("errorMessage", "Your account is no longer active.");
        } else if (logout) {
            model.addAttribute("activeTab", "signin");
            model.addAttribute("successMessage", "You have been signed out.");
        } else if (registered) {
            model.addAttribute("activeTab", "signin");
            model.addAttribute("successMessage", "Account created successfully. Please sign in.");
        }

        return "login";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequestDTO registerRequest,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeTab", "signup");
            model.addAttribute("errorMessage", "Please correct the registration form.");
            return "login";
        }

        try {
            userService.register(registerRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Account created successfully. Please sign in.");
            return "redirect:/auth/login?registered=true";
        } catch (DuplicateResourceException | IllegalArgumentException ex) {
            model.addAttribute("activeTab", "signup");
            model.addAttribute("errorMessage", ex.getMessage());
            return "login";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        if (!model.containsAttribute("forgotPasswordRequest")) {
            model.addAttribute("forgotPasswordRequest", new ForgotPasswordDTO());
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordRequest") ForgotPasswordDTO dto,
                                 BindingResult bindingResult,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Please provide a valid email address.");
            return "forgot-password";
        }

        userService.forgotPassword(dto.getEmail());
        model.addAttribute("successMessage", "If the email exists, reset instructions have been generated.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (!model.containsAttribute("resetPasswordRequest")) {
            ResetPasswordDTO dto = new ResetPasswordDTO();
            dto.setResetToken(token);
            model.addAttribute("resetPasswordRequest", dto);
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordDTO dto,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", "Please correct the reset password form.");
            return "reset-password";
        }

        try {
            userService.resetPassword(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Password has been reset successfully. Please sign in.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "reset-password";
        }
    }
}
