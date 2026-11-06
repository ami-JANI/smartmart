package com.smartmart.auth.service;

import java.util.Objects;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.smartmart.auth.domain.User;

/**
 * The logged-in user as Spring Security holds it, with the user's id, so the
 * session can be checked against the database even after the username changes.
 * Each role becomes the authority {@code ROLE_<name>}.
 */
public class AccountDetails extends org.springframework.security.core.userdetails.User {

    private final Long id;

    AccountDetails(User user) {
        super(user.getUsername(), user.getPasswordHash(), user.isActive(), true, true, true,
                user.roleNames().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
        this.id = user.getId();
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccountDetails account && Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
