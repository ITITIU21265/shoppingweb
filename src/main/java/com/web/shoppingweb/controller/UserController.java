package com.web.shoppingweb.controller;

import com.web.shoppingweb.dto.UpdateProfileDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    // Task 7.1: View profile
    @GetMapping("/profile")
    public ResponseEntity<UserResponseDTO> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        UserResponseDTO user = userService.getCurrentUser(username);
        return ResponseEntity.ok(user);
    }

    // Task 7.2: Update profile
    @PutMapping("/profile")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @Valid @RequestBody UpdateProfileDTO dto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        UserResponseDTO updated = userService.updateProfile(username, dto);
        return ResponseEntity.ok(updated);
    }

    // Task 7.3: Delete account (soft delete)
    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteAccount(@RequestParam String password) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        userService.deleteAccount(username, password);

        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Account deactivated successfully");
        return ResponseEntity.ok(resp);
    }
}
