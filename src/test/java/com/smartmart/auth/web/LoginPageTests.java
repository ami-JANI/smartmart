package com.smartmart.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;
import com.smartmart.auth.repository.RoleRepository;
import com.smartmart.auth.repository.UserRepository;

/**
 * Logs in through the real form against the two databases from compose.yaml.
 * Each test creates its own cashier and removes it afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginPageTests {

    private static final String PASSWORD = "correct-horse";

    private final MockMvc mvc;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private User cashier;

    @Autowired
    LoginPageTests(MockMvc mvc, UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.mvc = mvc;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void createCashier() {
        String username = "test-" + UUID.randomUUID().toString().substring(0, 8);
        cashier = users.save(new User(username, "Test Cashier", passwordEncoder.encode(PASSWORD),
                Set.of(roles.findByName(RoleName.CASHIER).orElseThrow())));
    }

    @AfterEach
    void removeCashier() {
        users.delete(cashier);
    }

    @Test
    void loginPageShowsTheForm() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")));
    }

    @Test
    void correctPasswordLogsInWithTheUsersRoles() throws Exception {
        mvc.perform(formLogin().user(cashier.getUsername()).password(PASSWORD))
                .andExpect(authenticated().withUsername(cashier.getUsername()).withRoles("CASHIER"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void wrongPasswordShowsTheError() throws Exception {
        mvc.perform(formLogin().user(cashier.getUsername()).password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Invalid username or password")));
    }

    @Test
    void unknownUsernameGetsTheSameError() throws Exception {
        mvc.perform(formLogin().user("no-such-user").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        cashier.deactivate();
        users.save(cashier);

        mvc.perform(formLogin().user(cashier.getUsername()).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutReturnsToTheLoginPage() throws Exception {
        mvc.perform(logout())
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void protectedPageSendsAnonymousUserToLogin() throws Exception {
        mvc.perform(get("/pos"))
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void homeSendsEachRoleToItsStartPage() throws Exception {
        mvc.perform(get("/").with(user("c").roles("CASHIER"))).andExpect(redirectedUrl("/pos"));
        mvc.perform(get("/").with(user("i").roles("INVENTORY_STAFF"))).andExpect(redirectedUrl("/products"));
        mvc.perform(get("/").with(user("m").roles("MANAGER"))).andExpect(redirectedUrl("/products"));
        mvc.perform(get("/").with(user("a").roles("ADMIN", "CASHIER"))).andExpect(redirectedUrl("/users"));
    }

    @Test
    void menuShowsTheLoggedInUser() throws Exception {
        mvc.perform(get("/categories").with(user("amina").roles("INVENTORY_STAFF")))
                .andExpect(content().string(containsString("amina")))
                .andExpect(content().string(containsString("Log out")));
    }

    @Test
    void passwordsAreHashedWithBcryptStrength12() {
        assertThat(cashier.getPasswordHash()).startsWith("$2a$12$").doesNotContain(PASSWORD);
    }
}
