package com.smartmart.auth.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.auth.domain.Role;
import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.RoleRepository;
import com.smartmart.auth.repository.UserRepository;

/**
 * Creates the first administrator at startup from {@code ADMIN_USERNAME} and
 * {@code ADMIN_PASSWORD} (FR-AUTH-3). An existing user with that name is left
 * unchanged. If either variable is empty, no account is created.
 */
@Component
class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    AdminAccountSeeder(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                       @Value("${smartmart.admin.username}") String username,
                       @Value("${smartmart.admin.password}") String password) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.warn("ADMIN_USERNAME or ADMIN_PASSWORD is not set, so no administrator was created");
            return;
        }
        if (users.existsByUsername(username)) {
            return;
        }
        Role admin = roles.findByName(RoleName.ADMIN).orElseThrow();
        users.save(new User(username, "Administrator", passwordEncoder.encode(password), Set.of(admin)));
        log.info("Created the administrator account {}", username);
    }
}
