package com.smartmart.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.auth.domain.Role;
import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.RoleRepository;
import com.smartmart.auth.repository.UserRepository;

class UserServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserServiceImpl service = new UserServiceImpl(users, roles, encoder);

    @BeforeEach
    void knowTheRoles() {
        for (RoleName name : RoleName.values()) {
            when(roles.findByName(name)).thenReturn(Optional.of(new Role(name)));
        }
        when(encoder.encode(any())).thenAnswer(call -> "hash:" + call.getArgument(0));
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private User stored(long id, String username, RoleName... roleNames) {
        Set<Role> held = new HashSet<>();
        for (RoleName name : roleNames) {
            held.add(new Role(name));
        }
        User user = new User(username, "Name", "old-hash", held);
        ReflectionTestUtils.setField(user, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private static UserData data(String username, String password, RoleName... roleNames) {
        return new UserData(username, "Full Name", password, Set.of(roleNames));
    }

    @Test
    void createHashesThePasswordAndKeepsEveryRole() {
        service.create(data(" karim ", "long-enough", RoleName.CASHIER, RoleName.INVENTORY_STAFF));

        verify(users).saveAndFlush(argThat(user ->
                user.getUsername().equals("karim")
                        && user.getPasswordHash().equals("hash:long-enough")
                        && user.roleNames().equals(Set.of(RoleName.CASHIER, RoleName.INVENTORY_STAFF))));
    }

    @Test
    void createRefusesATakenUsername() {
        when(users.existsByUsername("karim")).thenReturn(true);

        assertThatThrownBy(() -> service.create(data("karim", "long-enough", RoleName.CASHIER)))
                .isInstanceOf(UserRuleException.class)
                .hasMessage("Username already taken")
                .extracting("field").isEqualTo("username");
    }

    @Test
    void createTurnsADatabaseDuplicateIntoTheSameMessage() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_users_username"));

        assertThatThrownBy(() -> service.create(data("karim", "long-enough", RoleName.CASHIER)))
                .hasMessage("Username already taken");
    }

    @Test
    void createAcceptsEightCharactersAndRefusesSeven() {
        service.create(data("karim", "12345678", RoleName.CASHIER));

        assertThatThrownBy(() -> service.create(data("rahim", "1234567", RoleName.CASHIER)))
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void createRefusesNoRoles() {
        assertThatThrownBy(() -> service.create(data("karim", "long-enough")))
                .hasMessage("Choose at least one role");
    }

    @Test
    void updateWithoutPasswordKeepsTheOldOne() {
        User user = stored(5L, "karim", RoleName.CASHIER);

        service.update(5L, data("karim", "", RoleName.MANAGER));

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        assertThat(user.roleNames()).containsExactly(RoleName.MANAGER);
    }

    @Test
    void updateWithPasswordReplacesIt() {
        User user = stored(5L, "karim", RoleName.CASHIER);

        service.update(5L, data("karim", "new-password", RoleName.CASHIER));

        assertThat(user.getPasswordHash()).isEqualTo("hash:new-password");
    }

    @Test
    void updateRefusesRemovingTheLastAdministrator() {
        User admin = stored(1L, "admin", RoleName.ADMIN);
        when(users.countByActiveTrueAndRoles_Name(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, data("admin", "", RoleName.MANAGER)))
                .hasMessage("At least one active administrator must remain");
        assertThat(admin.roleNames()).containsExactly(RoleName.ADMIN);
    }

    @Test
    void updateAllowsRemovingAdministratorWhenAnotherRemains() {
        User admin = stored(1L, "admin", RoleName.ADMIN);
        when(users.countByActiveTrueAndRoles_Name(RoleName.ADMIN)).thenReturn(2L);

        service.update(1L, data("admin", "", RoleName.MANAGER));

        assertThat(admin.roleNames()).containsExactly(RoleName.MANAGER);
    }

    @Test
    void updateRefusesAUsernameTakenByAnotherUser() {
        stored(5L, "karim", RoleName.CASHIER);
        when(users.existsByUsernameAndIdNot("rahim", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, data("rahim", "", RoleName.CASHIER)))
                .hasMessage("Username already taken");
    }

    @Test
    void updateOfAMissingUserIsNotFound() {
        when(users.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, data("x", "", RoleName.CASHIER)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deactivateStopsTheUser() {
        User user = stored(5L, "karim", RoleName.CASHIER);

        service.deactivate(5L, "admin");

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void administratorCannotDeactivateThemselves() {
        User admin = stored(1L, "admin", RoleName.ADMIN);

        assertThatThrownBy(() -> service.deactivate(1L, "admin"))
                .hasMessage("You cannot deactivate your own account");
        assertThat(admin.isActive()).isTrue();
        verify(users, never()).saveAndFlush(any());
    }
}
