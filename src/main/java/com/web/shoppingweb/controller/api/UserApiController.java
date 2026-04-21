package com.web.shoppingweb.controller.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.ChangePasswordDTO;
import com.web.shoppingweb.dto.DeleteAccountDTO;
import com.web.shoppingweb.dto.UpdateProfileDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
public class UserApiController {

    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponseDTO> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication)));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponseDTO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto,
                                                         Authentication authentication) {
        return ResponseEntity.ok(userService.updateProfile(SecurityUtils.requireCurrentUsername(authentication), dto));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                                              Authentication authentication) {
        userService.changePassword(SecurityUtils.requireCurrentUsername(authentication), dto);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteAccount(@Valid @RequestBody DeleteAccountDTO dto,
                                                             Authentication authentication) {
        userService.deleteAccount(SecurityUtils.requireCurrentUsername(authentication), dto.getPassword());
        return ResponseEntity.ok(Map.of("message", "Account deactivated successfully"));
    }
}
