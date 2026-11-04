package com.smartmart.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Loads a user and their roles for Spring Security's login. Each role becomes
 * the authority {@code ROLE_<name>}. A deactivated user is returned disabled,
 * so the login fails with the same message as a wrong password.
 */
@Service
@RequiredArgsConstructor
class AccountDetailsService implements UserDetailsService {

    private final UserRepository users;

    /** Not read-only, so it reads from the primary and sees a change made a moment ago (GUIDELINES.md section 8.2). */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .roles(user.roleNames().stream().map(Enum::name).toArray(String[]::new))
                .build();
    }
}
