package com.web.shoppingweb.controller.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.service.SavedService;

@Controller
@RequestMapping("/saved")
@PreAuthorize("isAuthenticated()")
public class SavedController {

    private static final Logger log = LoggerFactory.getLogger(SavedController.class);

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

    @PostMapping(value = "/{productId}/toggle", produces = MediaType.TEXT_HTML_VALUE)
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

    @PostMapping(value = "/{productId}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> toggleSavedJson(@PathVariable Long productId,
                                             Authentication authentication) {
        String username = SecurityUtils.requireCurrentUsername(authentication);
        try {
            boolean saved = savedService.toggleSavedProduct(username, productId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("saved", saved);
            response.put("savedItemCount", savedService.countSavedItems(username));
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException ex) {
            return jsonError(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return jsonError(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to toggle saved product {} for user {}", productId, username, ex);
            return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to update saved items.");
        }
    }

    private ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null || message.isBlank() ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(body);
    }
}
