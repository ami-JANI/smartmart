package com.smartmart.auth.web;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Reloads the logged-in user's roles from the primary on every request, so a
 * role change made by an administrator applies at the user's next request
 * (FR-AUTH-7). A user deactivated since logging in is logged out.
 * <p>
 * Users are never deleted, so a username that is not found can only be a
 * test's mock user; it is left as it is.
 */
class FreshRolesFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    FreshRolesFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof UsernamePasswordAuthenticationToken && current.isAuthenticated()) {
            refresh(current, request);
        }
        chain.doFilter(request, response);
    }

    private void refresh(Authentication current, HttpServletRequest request) {
        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(current.getName());
        } catch (UsernameNotFoundException notInDatabase) {
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (user.isEnabled()) {
            UsernamePasswordAuthenticationToken fresh =
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            fresh.setDetails(current.getDetails());
            context.setAuthentication(fresh);
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        SecurityContextHolder.setContext(context);
    }
}
