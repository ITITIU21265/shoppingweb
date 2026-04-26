package com.web.shoppingweb.controller.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web.shoppingweb.dto.auth.ForgotPasswordDTO;
import com.web.shoppingweb.dto.auth.LoginRequestDTO;
import com.web.shoppingweb.dto.auth.LoginResponseDTO;
import com.web.shoppingweb.dto.auth.RefreshTokenDTO;
import com.web.shoppingweb.dto.auth.RegisterRequestDTO;
import com.web.shoppingweb.dto.auth.ResetPasswordDTO;
import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.security.SecurityUtils;
import com.web.shoppingweb.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;

    public AuthApiController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        userService.forgotPassword(dto.getEmail());
        return ResponseEntity.ok(Map.of("message", "If the email exists, a 6-digit OTP has been sent and will expire in 15 minutes"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return ResponseEntity.ok(userService.refreshToken(dto.getRefreshToken()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(SecurityUtils.requireCurrentUsername(authentication)));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenDTO dto,
                                                      Authentication authentication) {
        userService.logout(SecurityUtils.requireCurrentUsername(authentication), dto.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
