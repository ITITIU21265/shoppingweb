package com.web.shoppingweb.service;

import java.util.List;

import com.web.shoppingweb.dto.*;     

public interface UserService {

    LoginResponseDTO login(LoginRequestDTO loginRequest);

    UserResponseDTO register(RegisterRequestDTO registerRequest);

    UserResponseDTO getCurrentUser(String username);

    void changePassword(String username, ChangePasswordDTO dto);

    String forgotPassword(String email);

    void resetPassword(ResetPasswordDTO dto);

    UserResponseDTO updateProfile(String username, UpdateProfileDTO dto);

    void deleteAccount(String username, String password);

    // 8.1 – list all users (admin only)
    List<UserResponseDTO> getAllUsers();

    // 8.2 – update user role
    UserResponseDTO updateUserRole(Long id, UpdateRoleDTO dto);

    // 8.3 – toggle active / inactive
    UserResponseDTO toggleUserStatus(Long id);

    LoginResponseDTO refreshToken(String refreshToken);
}
