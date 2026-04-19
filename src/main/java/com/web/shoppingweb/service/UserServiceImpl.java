package com.web.shoppingweb.service;

import com.web.shoppingweb.dto.*;
import com.web.shoppingweb.entity.PasswordResetToken;
import com.web.shoppingweb.entity.RefreshToken;
import com.web.shoppingweb.entity.Role;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.entity.UserStatus;
import com.web.shoppingweb.exception.DuplicateResourceException;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.PasswordResetTokenRepository;
import com.web.shoppingweb.repository.RefreshTokenRepository;
import com.web.shoppingweb.repository.RoleRepository;
import com.web.shoppingweb.repository.UserRepository;
import com.web.shoppingweb.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private static final long REFRESH_TOKEN_DAYS = 7L;
    private static final long RESET_TOKEN_HOURS = 1L;

    //LOGIN + REFRESH TOKEN (Exercise 9.2)
    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {

        // 1. Authenticate username + password
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 2. Generate access token (JWT)
        String accessToken = tokenProvider.generateToken(authentication);

        // 3. Get user from DB
        String identifier = loginRequest.getUsername();
        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureActive(user);

        // 4. Keep a single active refresh token per user in the current project scope
        revokeAllRefreshTokens(user);
        RefreshToken refreshToken = createRefreshToken(user);

        // 5. Return both tokens
        return new LoginResponseDTO(
                accessToken,
                refreshToken.getToken(),
                user.getUsername(),
                user.getEmail(),
                getPrimaryRoleCode(user)
        );
    }

    //REGISTER
    @Override
    public UserResponseDTO register(RegisterRequestDTO registerRequest) {
        String username = registerRequest.getUsername().trim();
        String email = registerRequest.getEmail().trim();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists");
        }

        if (userRepository.findByEmail(username).isPresent()) {
            throw new DuplicateResourceException("Username must not match an existing email");
        }

        if (userRepository.findByUsername(email).isPresent()) {
            throw new DuplicateResourceException("Email must not match an existing username");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName().trim());
        user.setRoles(java.util.Set.of(getRoleByCode("CUSTOMER")));
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        return convertToDTO(savedUser);
    }

    //CHANGE PASSWORD (Exercise 6.1)
    @Override
    public void changePassword(String username, ChangePasswordDTO dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureActive(user);

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        revokeAllRefreshTokens(user);
    }

    //FORGOT / RESET PASSWORD (Exercise 6.2)
    @Override
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    passwordResetTokenRepository.deleteByUser(user);
                    passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setToken(UUID.randomUUID().toString());
                    resetToken.setExpiresAt(LocalDateTime.now().plusHours(RESET_TOKEN_HOURS));

                    passwordResetTokenRepository.save(resetToken);
                });
    }

    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(dto.getResetToken())
                .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid"));

        User user = resetToken.getUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Reset token is no longer valid");
        }

        if (resetToken.getUsedAt() != null) {
            throw new IllegalArgumentException("Reset token has already been used");
        }

        if (resetToken.getExpiresAt() == null || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
        revokeAllRefreshTokens(user);
    }

    //ADMIN FEATURES (Exercise 8)
    @Override
    public List<UserResponseDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponseDTO updateUserRole(Long id, UpdateRoleDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setRoles(java.util.Set.of(getRoleByCode(dto.getRoleCode())));
        User saved = userRepository.save(user);

        return convertToDTO(saved);
    }

    @Override
    public UserResponseDTO toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.BLOCKED);
            revokeAllRefreshTokens(user);
        } else {
            user.setStatus(UserStatus.ACTIVE);
        }
        User saved = userRepository.save(user);

        return convertToDTO(saved);
    }

    //PROFILE FEATURES (Exercise 7)
    @Override
    public UserResponseDTO updateProfile(String username, UpdateProfileDTO dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureActive(user);
        if (dto.getEmail() != null &&
                !dto.getEmail().equalsIgnoreCase(user.getEmail()) &&
                userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        if (dto.getEmail() != null
                && !dto.getEmail().isBlank()
                && userRepository.findByUsername(dto.getEmail().trim())
                    .filter(other -> !other.getId().equals(user.getId()))
                    .isPresent()) {
            throw new DuplicateResourceException("Email must not match an existing username");
        }

        user.setFullName(dto.getFullName().trim());
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail().trim());
        }

        User saved = userRepository.save(user);
        return convertToDTO(saved);
    }

    @Override
    public void deleteAccount(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureActive(user);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Password is incorrect");
        }

        // Soft delete
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
        revokeAllRefreshTokens(user);
    }

    @Override
    public void logout(String username, String refreshToken) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        RefreshToken storedToken = refreshTokenRepository.findByTokenAndUser(refreshToken, user)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid"));

        refreshTokenRepository.delete(storedToken);
    }

    //REFRESH TOKEN (Exercise 9.3)
    @Override
    public LoginResponseDTO refreshToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid"));

        if (refreshToken.getExpiryDate() == null ||
                refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadCredentialsException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        if (user == null) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadCredentialsException("Refresh token is invalid");
        }
        ensureActive(user);

        String newAccessToken = tokenProvider.generateTokenFromUsername(user.getUsername());
        refreshTokenRepository.delete(refreshToken);
        RefreshToken rotatedToken = createRefreshToken(user);

        return new LoginResponseDTO(
                newAccessToken,
                rotatedToken.getToken(),
                user.getUsername(),
                user.getEmail(),
                getPrimaryRoleCode(user)
        );
    }

    // COMMON METHODS
    @Override
    public UserResponseDTO getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureActive(user);

        return convertToDTO(user);
    }

    private UserResponseDTO convertToDTO(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                getPrimaryRoleCode(user),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }

    private Role getRoleByCode(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + code));
    }

    private String getPrimaryRoleCode(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return "CUSTOMER";
        }
        for (String code : java.util.List.of("ADMIN", "SELLER", "CUSTOMER")) {
            boolean match = user.getRoles().stream()
                    .anyMatch(role -> code.equalsIgnoreCase(role.getCode()));
            if (match) {
                return code;
            }
        }
        return user.getRoles().iterator().next().getCode();
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        return refreshTokenRepository.save(refreshToken);
    }

    private void revokeAllRefreshTokens(User user) {
        refreshTokenRepository.deleteAllByUserValue(user);
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            revokeAllRefreshTokens(user);
            throw new DisabledException("Account is no longer active");
        }
    }
}
