package com.web.shoppingweb.service;

import com.web.shoppingweb.dto.*;
import com.web.shoppingweb.entity.RefreshToken;
import com.web.shoppingweb.entity.Role;
import com.web.shoppingweb.entity.User;
import com.web.shoppingweb.entity.UserStatus;
import com.web.shoppingweb.exception.DuplicateResourceException;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.RefreshTokenRepository;
import com.web.shoppingweb.repository.RoleRepository;
import com.web.shoppingweb.repository.UserRepository;
import com.web.shoppingweb.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
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
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long REFRESH_TOKEN_DAYS = 7L;

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
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 4. Create refresh token (valid 7 days)
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));

        refreshTokenRepository.save(refreshToken);

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
        // Check if username exists
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Check if email exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName());
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

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    //FORGOT / RESET PASSWORD (Exercise 6.2)
    @Override
    public String forgotPassword(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        jdbcTemplate.update(
                "INSERT INTO password_resets (user_id, reset_token, expires_at) VALUES (?, ?, ?)",
                user.getId(),
                token,
                Timestamp.valueOf(expiresAt)
        );

        return token;
    }

    @Override
    public void resetPassword(ResetPasswordDTO dto) {

        Long userId;
        LocalDateTime expiresAt;
        Timestamp usedAt;
        try {
            var row = jdbcTemplate.queryForMap(
                    "SELECT user_id, expires_at, used_at FROM password_resets WHERE reset_token = ?",
                    dto.getResetToken()
            );
            userId = ((Number) row.get("user_id")).longValue();
            expiresAt = ((Timestamp) row.get("expires_at")).toLocalDateTime();
            usedAt = (Timestamp) row.get("used_at");
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Invalid reset token");
        }

        if (usedAt != null) {
            throw new IllegalArgumentException("Reset token has already been used");
        }

        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        jdbcTemplate.update(
                "UPDATE password_resets SET used_at = ? WHERE reset_token = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                dto.getResetToken()
        );
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
        if (dto.getEmail() != null &&
                !dto.getEmail().equalsIgnoreCase(user.getEmail()) &&
                userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        user.setFullName(dto.getFullName());
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail());
        }

        User saved = userRepository.save(user);
        return convertToDTO(saved);
    }

    @Override
    public void deleteAccount(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Password is incorrect");
        }

        // Soft delete
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }

    //REFRESH TOKEN (Exercise 9.3)
    @Override
    public LoginResponseDTO refreshToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid refresh token"));

        if (refreshToken.getExpiryDate() == null ||
                refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        User user = refreshToken.getUser();

        String newAccessToken = tokenProvider.generateTokenFromUsername(user.getUsername());

        return new LoginResponseDTO(
                newAccessToken,
                refreshTokenStr,
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
}
