package com.smartmart.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smartmart.auth.domain.Role;
import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.RoleRepository;
import com.smartmart.auth.repository.UserRepository;

class AdminAccountSeederTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private AdminAccountSeeder seeder(String username, String password) {
        return new AdminAccountSeeder(users, roles, encoder, username, password);
    }

    @Test
    void createsTheAdministratorWithAHashedPassword() {
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(new Role(RoleName.ADMIN)));
        when(encoder.encode("s3cret-pass")).thenReturn("$2a$12$hash");

        seeder("admin", "s3cret-pass").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(saved.getValue().roleNames()).containsExactly(RoleName.ADMIN);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void leavesAnExistingUserUnchanged() {
        when(users.existsByUsername("admin")).thenReturn(true);

        seeder("admin", "s3cret-pass").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void createsNothingWhenAVariableIsEmpty() {
        seeder("", "s3cret-pass").run(null);
        seeder("admin", "").run(null);

        verify(users, never()).save(any());
    }
}
