package com.smartmart.auth.web;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.smartmart.auth.service.AccountDetails;
import com.smartmart.auth.service.AccountDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Reloads the logged-in user from the primary on every request, by user id, so
 * an administrator's change applies at the user's next request (FR-AUTH-7):
 * new roles, a new username, or deactivation, which logs the user out. A user
 * who no longer exists is logged out too.
 * <p>
 * Only logins made through the login form carry an id. Other principals, such
 * as the tests' mock users, are left as they are.
 */
class FreshRolesFilter extends OncePerRequestFilter {

    private final AccountDetailsService accounts;

    FreshRolesFilter(AccountDetailsService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof UsernamePasswordAuthenticationToken && current.getPrincipal() instanceof AccountDetails account) {
            refresh(current, account, request);
        }
        chain.doFilter(request, response);
    }

    private void refresh(Authentication current, AccountDetails account, HttpServletRequest request) {
        Optional<AccountDetails> fresh = accounts.loadById(account.getId()).filter(AccountDetails::isEnabled);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (fresh.isPresent()) {
            AccountDetails user = fresh.get();
            user.eraseCredentials();
            UsernamePasswordAuthenticationToken token =
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            token.setDetails(current.getDetails());
            context.setAuthentication(token);
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        SecurityContextHolder.setContext(context);
    }
}
