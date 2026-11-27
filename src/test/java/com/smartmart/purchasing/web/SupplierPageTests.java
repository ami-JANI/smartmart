package com.smartmart.purchasing.web;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.smartmart.purchasing.repository.SupplierRepository;
import com.smartmart.purchasing.service.SupplierData;
import com.smartmart.purchasing.service.SupplierService;

/**
 * Runs the real pages, service and table against the two databases from
 * compose.yaml. The list reads from the replica, so checks of what it shows
 * wait for replication, polling on the test thread to keep its mock user.
 * Each test removes the suppliers it created, all named with this run's marker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "INVENTORY_STAFF")
class SupplierPageTests {

    private final MockMvc mvc;
    private final TransactionTemplate writes;
    private final SupplierService suppliers;
    private final SupplierRepository repository;
    private final String marker = "Test-" + UUID.randomUUID();

    @Autowired
    SupplierPageTests(MockMvc mvc, SupplierService suppliers, SupplierRepository repository, TransactionTemplate writes) {
        this.mvc = mvc;
        this.suppliers = suppliers;
        this.repository = repository;
        this.writes = writes;
    }

    @AfterEach
    void removeTestData() {
        // One read-write transaction, so the reads below go to the primary and see rows
        // created a moment ago; a read-only read could reach a replica that is behind.
        writes.executeWithoutResult(status -> {
            repository.findAll().stream()
                    .filter(supplier -> supplier.getName().contains(marker))
                    .forEach(repository::delete);
        });
    }

    private Long supplier(String name, String contact, String phone) {
        return suppliers.create(new SupplierData(marker + " " + name, contact, phone, null, null, true));
    }

    private void awaitList(String expectedText) {
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/suppliers").param("q", marker).param("size", "100"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString(expectedText))));
    }

    private MockHttpServletRequestBuilder validForm(String url, String name) {
        return post(url).with(csrf())
                .param("name", name)
                .param("contactPerson", "Rahim")
                .param("phone", "+880 1700-000000")
                .param("email", "sales@example.com")
                .param("address", "Dhaka")
                .param("active", "true");
    }

    @Test
    void newFormRendersWithActiveTicked() throws Exception {
        mvc.perform(get("/suppliers/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New supplier")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("checked=\"checked\"")));
    }

    @Test
    void creatingASupplierRedirectsAndItAppearsInTheList() throws Exception {
        String name = marker + " Acme";

        mvc.perform(validForm("/suppliers", name))
                .andExpect(redirectedUrl("/suppliers"))
                .andExpect(flash().attribute("message", "Supplier created"));

        awaitList(name);
        mvc.perform(get("/suppliers").param("q", marker))
                .andExpect(content().string(containsString("+880 1700-000000")))
                .andExpect(content().string(containsString("sales@example.com")))
                .andExpect(content().string(containsString("Active")));
    }

    @Test
    void brokenFieldsShowTheirMessages() throws Exception {
        mvc.perform(post("/suppliers").with(csrf())
                        .param("name", " ")
                        .param("phone", "abc")
                        .param("email", "not-an-email")
                        .param("contactPerson", "x".repeat(101))
                        .param("address", "x".repeat(256)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Phone must be 6 to 20 digits")))
                .andExpect(content().string(containsString("Enter a valid email address")))
                .andExpect(content().string(containsString("Contact person can have at most 100 characters")))
                .andExpect(content().string(containsString("Address can have at most 255 characters")));
    }

    @Test
    void phoneLengthBoundaries() throws Exception {
        mvc.perform(post("/suppliers").with(csrf()).param("name", marker + " five").param("phone", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phone must be 6 to 20")));
        mvc.perform(post("/suppliers").with(csrf()).param("name", marker + " six").param("phone", "123456"))
                .andExpect(redirectedUrl("/suppliers"));
        mvc.perform(post("/suppliers").with(csrf()).param("name", marker + " twenty").param("phone", "1".repeat(20)))
                .andExpect(redirectedUrl("/suppliers"));
        mvc.perform(post("/suppliers").with(csrf()).param("name", marker + " long").param("phone", "1".repeat(21)))
                .andExpect(status().isOk());
    }

    @Test
    void takenNameShowsTheFieldError() throws Exception {
        supplier("Acme", null, "01700000000");

        mvc.perform(validForm("/suppliers", marker + " Acme"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Supplier name already in use")));
    }

    @Test
    void editFormShowsTheSupplierAndSavingChangesIt() throws Exception {
        Long id = supplier("Before", "Rahim", "01700000000");

        mvc.perform(get("/suppliers/" + id + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit supplier")))
                .andExpect(content().string(containsString("Before")));

        mvc.perform(validForm("/suppliers/" + id, marker + " After"))
                .andExpect(redirectedUrl("/suppliers"))
                .andExpect(flash().attribute("message", "Supplier saved"));

        mvc.perform(get("/suppliers/" + id + "/edit"))
                .andExpect(content().string(containsString("After")));
    }

    @Test
    void savingOnEditKeepsItsOwnName() throws Exception {
        Long id = supplier("Acme", null, "01700000000");

        mvc.perform(validForm("/suppliers/" + id, marker + " Acme"))
                .andExpect(flash().attribute("message", "Supplier saved"));
    }

    @Test
    void untickingActiveMarksTheSupplierInactiveAndItStaysListed() throws Exception {
        Long id = supplier("Acme", null, "01700000000");

        mvc.perform(post("/suppliers/" + id).with(csrf())
                        .param("name", marker + " Acme")
                        .param("phone", "01700000000")
                        .param("_active", "on"))
                .andExpect(redirectedUrl("/suppliers"));

        awaitList("Inactive");
    }

    @Test
    void searchMatchesNameContactOrPhoneIgnoringCase() throws Exception {
        String contact = "Karim-" + UUID.randomUUID();
        String phone = "01" + (100_000_000 + (long) (Math.random() * 899_999_999));
        supplier("Rice Traders", contact, phone);

        awaitList("Rice Traders");
        mvc.perform(get("/suppliers").param("q", marker + " RICE"))
                .andExpect(content().string(containsString("Rice Traders")));
        mvc.perform(get("/suppliers").param("q", contact.toLowerCase()))
                .andExpect(content().string(containsString("Rice Traders")));
        mvc.perform(get("/suppliers").param("q", phone))
                .andExpect(content().string(containsString("Rice Traders")));
        mvc.perform(get("/suppliers").param("q", marker + " nothing"))
                .andExpect(content().string(containsString("No suppliers found")));
    }

    @Test
    void pagingShowsOnePageAtATimeInNameOrder() throws Exception {
        supplier("Aaa", null, "01700000001");
        supplier("Bbb", null, "01700000002");

        awaitList("2 suppliers");
        mvc.perform(get("/suppliers").param("q", marker).param("size", "1").param("page", "1"))
                .andExpect(content().string(containsString("Bbb")))
                .andExpect(content().string(not(containsString("Aaa"))))
                .andExpect(content().string(containsString("Page 2 of 2")));
    }

    @Test
    void missingSupplierShowsNotFound() throws Exception {
        mvc.perform(get("/suppliers/999999999/edit")).andExpect(status().isNotFound());
    }

    @Test
    void otherRolesAreRefused() throws Exception {
        for (String role : new String[] {"MANAGER", "CASHIER"}) {
            mvc.perform(get("/suppliers").with(user("someone").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/suppliers").with(user("someone").roles(role)).with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanAccessSuppliers() throws Exception {
        mvc.perform(get("/suppliers").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void menuShowsSuppliersOnlyToInventoryStaff() throws Exception {
        mvc.perform(get("/suppliers"))
                .andExpect(content().string(containsString("href=\"/suppliers\"")));
        mvc.perform(get("/products").with(user("someone").roles("MANAGER")))
                .andExpect(content().string(not(containsString("href=\"/suppliers\""))));
    }
}
