package com.web.shoppingweb.security;

import java.util.Arrays;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String requireCurrentUsername() {
        return requireCurrentUsername(SecurityContextHolder.getContext().getAuthentication());
    }

    public static String requireCurrentUsername(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }

        return username;
    }

    public static boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority -> Arrays.stream(roles)
                        .map(role -> "ROLE_" + role.toUpperCase())
                        .anyMatch(authority::equals));
    }
}
