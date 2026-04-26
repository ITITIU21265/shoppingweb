package com.web.shoppingweb.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.web.shoppingweb.entity.user.UserStatus;
import com.web.shoppingweb.repository.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserStatusEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserStatusEnforcementFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String username = authentication.getName();
            boolean inactive = userRepository.findByUsername(username)
                    .map(user -> user.getStatus() != UserStatus.ACTIVE)
                    .orElse(true);

            if (inactive) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect("/auth/login?inactive=true");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
