package com.smartmart.auth.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds the logged-in user's name and roles to every page model, for the
 * menu in {@code layout/base.html}. Both are null when nobody is logged in.
 */
@ControllerAdvice
class CurrentUserModel {

    private static final String ROLE_PREFIX = "ROLE_";

    @ModelAttribute("username")
    String username(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    @ModelAttribute("roles")
    List<String> roles(Authentication authentication) {
        return authentication == null ? null : roleNames(authentication);
    }

    /** Role names without the {@code ROLE_} prefix, such as {@code CASHIER}. */
    static List<String> roleNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
