package com.smartmart.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.auth.domain.Role;
import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.RoleRepository;
import com.smartmart.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class UserServiceImpl implements UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt reads at most 72 bytes of a password and refuses a longer one. */
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String USERNAME_TAKEN = "Username already taken";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public List<UserSummary> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserServiceImpl::summaryOf).toList();
    }

    @Override
    @Transactional
    public UserSummary get(Long id) {
        return summaryOf(find(id));
    }

    @Override
    @Transactional
    public Long create(UserData data) {
        requirePassword(data.password());
        if (users.existsByUsername(data.username().strip())) {
            throw new UserRuleException("username", USERNAME_TAKEN);
        }
        User user = new User(data.username().strip(), data.fullName().strip(),
                passwordEncoder.encode(data.password()), rolesFor(data));
        return saveChecked(user).getId();
    }

    @Override
    @Transactional
    public void update(Long id, UserData data) {
        User user = find(id);
        if (users.existsByUsernameAndIdNot(data.username().strip(), id)) {
            throw new UserRuleException("username", USERNAME_TAKEN);
        }
        Set<Role> newRoles = rolesFor(data);
        requireAnAdministratorRemains(user, newRoles);
        user.update(data.username().strip(), data.fullName().strip(), newRoles);
        if (!data.password().isEmpty()) {
            requirePassword(data.password());
            user.changePassword(passwordEncoder.encode(data.password()));
        }
        saveChecked(user);
    }

    @Override
    @Transactional
    public void deactivate(Long id, String currentUsername) {
        User user = find(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new UserRuleException(null, "You cannot deactivate your own account");
        }
        user.deactivate();
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void requirePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new UserRuleException("password", "Password must be at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new UserRuleException("password", "Password is too long: at most 72 English letters, digits or symbols");
        }
    }

    private Set<Role> rolesFor(UserData data) {
        if (data.roles().isEmpty()) {
            throw new UserRuleException("roles", "Choose at least one role");
        }
        return data.roles().stream()
                .map(name -> roles.findByName(name).orElseThrow())
                .collect(Collectors.toSet());
    }

    private void requireAnAdministratorRemains(User user, Set<Role> newRoles) {
        boolean losesAdmin = user.isActive() && user.roleNames().contains(RoleName.ADMIN)
                && newRoles.stream().noneMatch(role -> role.getName() == RoleName.ADMIN);
        if (losesAdmin && users.countByActiveTrueAndRoles_Name(RoleName.ADMIN) <= 1) {
            throw new UserRuleException("roles", "At least one active administrator must remain");
        }
    }

    // The database constraint catches two people saving the same username at once.
    private User saveChecked(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserRuleException("username", USERNAME_TAKEN);
        }
    }

    private static UserSummary summaryOf(User user) {
        List<String> roleNames = user.roleNames().stream().sorted().map(Enum::name).toList();
        return new UserSummary(user.getId(), user.getUsername(), user.getFullName(), roleNames, user.isActive());
    }
}
