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
                        .anyMatch(role -> authorityMatches(authority, role)));
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return hasAnyRole(authentication, role);
    }

    public static boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ADMIN");
    }

    public static boolean isSeller(Authentication authentication) {
        return hasRole(authentication, "SELLER");
    }

    public static boolean isCustomer(Authentication authentication) {
        return hasRole(authentication, "CUSTOMER");
    }

    public static boolean hasDashboardAccess(Authentication authentication) {
        return hasAnyRole(authentication, "ADMIN", "SELLER");
    }

    public static String resolveDashboardTarget(Authentication authentication) {
        if (!hasDashboardAccess(authentication)) {
            return isCustomer(authentication) ? "/catalog" : "/profile";
        }
        return "/dashboard?view=overview";
    }

    public static String resolvePostLoginTarget(Authentication authentication) {
        if (isAdmin(authentication) || isSeller(authentication)) {
            return "/dashboard?view=overview";
        }
        if (isCustomer(authentication)) {
            return "/catalog";
        }
        return "/profile";
    }

    private static boolean authorityMatches(String authority, String role) {
        if (authority == null || role == null) {
            return false;
        }

        String normalizedAuthority = authority.trim().toUpperCase();
        String normalizedRole = role.trim().toUpperCase();
        String roleAuthority = normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;

        return normalizedAuthority.equals(roleAuthority)
                || normalizedAuthority.equals(normalizedRole)
                || stripRolePrefix(normalizedAuthority).equals(stripRolePrefix(normalizedRole));
    }

    private static String stripRolePrefix(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        while (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}
