package com.smartmart.auth.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Loads a user and their roles for Spring Security: by username at login, and
 * by id on every later request. A deactivated user is returned disabled, so the
 * login fails with the same message as a wrong password.
 */
@Service
@RequiredArgsConstructor
public class AccountDetailsService implements UserDetailsService {

    private final UserRepository users;

    /** Not read-only, so it reads from the primary and sees a change made a moment ago (GUIDELINES.md section 8.2). */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(AccountDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username"));
    }

    /** The user as they are now, under their current username; empty if there is no such user. Reads from the primary. */
    @Transactional
    public Optional<AccountDetails> loadById(Long id) {
        return users.findById(id).map(AccountDetails::new);
    }
}
