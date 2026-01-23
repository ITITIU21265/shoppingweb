package com.web.shoppingweb.controller;

import com.web.shoppingweb.dto.UpdateRoleDTO;
import com.web.shoppingweb.dto.UserResponseDTO;
import com.web.shoppingweb.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    @Autowired
    private UserService userService;

    // 8.1 – List all users
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<UserResponseDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    // 8.2 – Update user role
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleDTO dto) {

        UserResponseDTO updated = userService.updateUserRole(id, dto);
        return ResponseEntity.ok(updated);
    }

    // 8.3 – Toggle active/inactive
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> toggleUserStatus(@PathVariable Long id) {
        UserResponseDTO updated = userService.toggleUserStatus(id);
        return ResponseEntity.ok(updated);
    }
}
