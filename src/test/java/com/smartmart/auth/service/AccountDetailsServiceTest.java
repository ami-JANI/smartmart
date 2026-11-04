package com.smartmart.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.smartmart.auth.domain.Role;
import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.UserRepository;

class AccountDetailsServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccountDetailsService service = new AccountDetailsService(users);

    private User stored(String username, RoleName... roles) {
        Set<Role> held = new HashSet<>();
        for (RoleName role : roles) {
            held.add(new Role(role));
        }
        User user = new User(username, "Test User", "$2a$12$hash", held);
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void eachRoleBecomesARoleAuthority() {
        stored("amina", RoleName.CASHIER, RoleName.INVENTORY_STAFF);

        UserDetails details = service.loadUserByUsername("amina");

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CASHIER", "ROLE_INVENTORY_STAFF");
        assertThat(details.getPassword()).isEqualTo("$2a$12$hash");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void deactivatedUserIsDisabled() {
        stored("rahim", RoleName.CASHIER).deactivate();

        assertThat(service.loadUserByUsername("rahim").isEnabled()).isFalse();
    }

    @Test
    void unknownUsernameIsRefused() {
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
