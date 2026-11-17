package com.smartmart.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Checks the access table in GUIDELINES.md section 7 (FR-AUTH-5): for each
 * role, a page it may open and pages it may not. "Allowed" means the request
 * got past the role check, so any status other than 401 and 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessTests {

    private final MockMvc mvc;

    @Autowired
    RoleAccessTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(user("test-" + role.toLowerCase()).roles(role)).with(csrf());
    }

    private void allowed(RequestBuilder request) throws Exception {
        int status = mvc.perform(request).andReturn().getResponse().getStatus();
        assertThat(status).as("status").isNotIn(401, 403);
    }

    private void refused(RequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isForbidden());
    }

    @Test
    void adminManagesUsersOnly() throws Exception {
        allowed(as("ADMIN", get("/users")));
        refused(as("ADMIN", get("/pos")));
        refused(as("ADMIN", get("/products")));
        refused(as("ADMIN", get("/categories")));
    }

    @Test
    void managerViewsProductsButCannotChangeThem() throws Exception {
        allowed(as("MANAGER", get("/products")));
        refused(as("MANAGER", get("/products/new")));
        refused(as("MANAGER", post("/products")));
        refused(as("MANAGER", get("/categories")));
        refused(as("MANAGER", get("/pos")));
        refused(as("MANAGER", get("/users")));
    }

    @Test
    void cashierUsesThePointOfSaleOnly() throws Exception {
        allowed(as("CASHIER", get("/pos")));
        refused(as("CASHIER", get("/products")));
        refused(as("CASHIER", get("/categories")));
        refused(as("CASHIER", get("/users")));
    }

    @Test
    void inventoryStaffMaintainsTheCatalogue() throws Exception {
        allowed(as("INVENTORY_STAFF", get("/categories")));
        allowed(as("INVENTORY_STAFF", get("/products")));
        allowed(as("INVENTORY_STAFF", get("/products/new")));
        refused(as("INVENTORY_STAFF", get("/pos")));
        refused(as("INVENTORY_STAFF", get("/users")));
    }

    @Test
    void unlistedPathIsDeniedToEveryRole() throws Exception {
        refused(as("ADMIN", get("/settings")));
    }

    @Test
    void anonymousPageRequestGoesToLogin() throws Exception {
        mvc.perform(get("/products")).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void anonymousApiRequestGets401() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyManagersOpenTheDashboardAndOnlyCashiersRegisterMembers() throws Exception {
        allowed(as("MANAGER", get("/dashboard")));
        refused(as("CASHIER", get("/dashboard")));
        refused(as("INVENTORY_STAFF", get("/dashboard")));
        refused(as("ADMIN", get("/dashboard")));
        allowed(as("CASHIER", get("/pos/members")));
        refused(as("MANAGER", get("/pos/members")));
    }
}
