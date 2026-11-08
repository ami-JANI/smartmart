package com.smartmart.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.UserRepository;
import com.smartmart.auth.service.UserData;
import com.smartmart.auth.service.UserService;

/**
 * Runs the real user pages against the two databases from compose.yaml. Page
 * requests are made as an administrator; the users being managed log in
 * through the real form. Each test removes the users it created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPageTests {

    private static final String PASSWORD = "long-enough";

    private final MockMvc mvc;
    private final UserService userService;
    private final UserRepository users;
    private final List<Long> created = new ArrayList<>();

    @Autowired
    UserPageTests(MockMvc mvc, UserService userService, UserRepository users) {
        this.mvc = mvc;
        this.userService = userService;
        this.users = users;
    }

    @AfterEach
    void removeTestUsers() {
        users.findAllById(created).forEach(users::delete);
    }

    private static RequestPostProcessor admin() {
        return user("test-admin").roles("ADMIN");
    }

    private String username() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createdUser(String username, RoleName... roles) {
        Long id = userService.create(new UserData(username, "Test User", PASSWORD, Set.of(roles)));
        created.add(id);
        return id;
    }

    private MockHttpSession loggedIn(String username) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin().user(username).password(PASSWORD))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void adminCreatesAUserWithTwoRoles() throws Exception {
        String username = username();

        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", username).param("fullName", "Karim Uddin").param("password", PASSWORD)
                        .param("roles", "CASHIER", "INVENTORY_STAFF"))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("message", "User created"));

        User saved = users.findByUsername(username).orElseThrow();
        created.add(saved.getId());
        assertThat(saved.roleNames()).containsExactlyInAnyOrder(RoleName.CASHIER, RoleName.INVENTORY_STAFF);
        mvc.perform(get("/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(username)))
                .andExpect(content().string(containsString("Karim Uddin")));
    }

    @Test
    void takenUsernameShowsNextToTheField() throws Exception {
        String username = username();
        createdUser(username, RoleName.CASHIER);

        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", username).param("fullName", "Someone").param("password", PASSWORD)
                        .param("roles", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username already taken")));
    }

    @Test
    void userWithoutRolesIsRefused() throws Exception {
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", username()).param("fullName", "Someone").param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose at least one role")));
    }

    @Test
    void editFormShowsTheCurrentValues() throws Exception {
        String username = username();
        Long id = createdUser(username, RoleName.MANAGER);

        mvc.perform(get("/users/{id}/edit", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + username + "\"")))
                .andExpect(content().string(containsString("Edit user")));
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        String username = username();
        Long id = createdUser(username, RoleName.CASHIER);

        mvc.perform(post("/users/{id}/deactivate", id).with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("message", "User deactivated"));

        mvc.perform(formLogin().user(username).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void deactivatingSomeoneLoggedInEndsTheirSession() throws Exception {
        String username = username();
        Long id = createdUser(username, RoleName.CASHIER);
        MockHttpSession session = loggedIn(username);

        userService.deactivate(id, "test-admin");

        mvc.perform(get("/pos").session(session)).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void roleChangeAppliesAtTheNextRequest() throws Exception {
        String username = username();
        Long id = createdUser(username, RoleName.CASHIER);
        MockHttpSession session = loggedIn(username);
        mvc.perform(get("/pos").session(session)).andExpect(status().isOk());

        userService.update(id, new UserData(username, "Test User", "", Set.of(RoleName.INVENTORY_STAFF)));

        mvc.perform(get("/pos").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/categories").session(session)).andExpect(status().isOk());
    }

    @Test
    void renamedUserIsStillCheckedAndCanBeLockedOut() throws Exception {
        String username = username();
        String renamed = username();
        Long id = createdUser(username, RoleName.CASHIER);
        MockHttpSession session = loggedIn(username);

        userService.update(id, new UserData(renamed, "Test User", "", Set.of(RoleName.CASHIER)));

        mvc.perform(get("/pos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(renamed)));

        userService.deactivate(id, "test-admin");

        mvc.perform(get("/pos").session(session)).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void renamedAdministratorStillCannotDeactivateTheirOwnAccount() throws Exception {
        String username = username();
        Long id = createdUser(username, RoleName.ADMIN);
        MockHttpSession session = loggedIn(username);

        userService.update(id, new UserData(username(), "Test User", "", Set.of(RoleName.ADMIN)));

        mvc.perform(post("/users/{id}/deactivate", id).session(session).with(csrf()))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("error", "You cannot deactivate your own account"));
        assertThat(users.findById(id).orElseThrow().isActive()).isTrue();
    }

    @Test
    void usernameIsTrimmedBeforeItsLengthIsChecked() throws Exception {
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", "  ab  ").param("fullName", "Someone").param("password", PASSWORD)
                        .param("roles", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username must be 3 to 50 characters")));

        String username = username();
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", "  " + username + "  ").param("fullName", "Someone").param("password", PASSWORD)
                        .param("roles", "CASHIER"))
                .andExpect(redirectedUrl("/users"));
        created.add(users.findByUsername(username).orElseThrow().getId());
    }

    @Test
    void emptyUsernameShowsOnlyTheRequiredMessage() throws Exception {
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", "").param("fullName", "Someone").param("password", PASSWORD)
                        .param("roles", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username is required")))
                .andExpect(content().string(not(containsString("Username must be 3 to 50 characters"))));
    }

    @Test
    void passwordOverTheBcryptLimitShowsAMessageInsteadOfAnError() throws Exception {
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", username()).param("fullName", "Someone").param("password", "p".repeat(73))
                        .param("roles", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password is too long")));

        String username = username();
        mvc.perform(post("/users").with(admin()).with(csrf())
                        .param("username", username).param("fullName", "Someone").param("password", "p".repeat(72))
                        .param("roles", "CASHIER"))
                .andExpect(redirectedUrl("/users"));
        created.add(users.findByUsername(username).orElseThrow().getId());
    }
}
