package com.smartmart.reports.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The dashboard renders against the real database, with the parts each role may see. */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardPageTests {

    private final MockMvc mvc;

    @Autowired
    DashboardPageTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void managerSeesEverySectionOfTheDashboard() throws Exception {
        mvc.perform(get("/dashboard").with(user("test-manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today's sales")))
                .andExpect(content().string(containsString("Sales, last 30 days")))
                .andExpect(content().string(containsString("Best sellers this month")))
                .andExpect(content().string(containsString("Top members this month")))
                .andExpect(content().string(containsString("Sales dropped")))
                .andExpect(content().string(containsString("Inventory status")))
                .andExpect(content().string(containsString("<polyline class=\"chart-line\"")));
    }

    @Test
    void cashierSeesTodayAndMembersButNotShopFigures() throws Exception {
        mvc.perform(get("/dashboard").with(user("test-cashier").roles("CASHIER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today's sales")))
                .andExpect(content().string(containsString("Best sellers this month")))
                .andExpect(content().string(not(containsString("Top members this month"))))
                .andExpect(content().string(not(containsString("Inventory status"))))
                .andExpect(content().string(not(containsString("This month</p>"))));
    }

    @Test
    void inventoryStaffSeesStockOnly() throws Exception {
        mvc.perform(get("/dashboard").with(user("test-stock").roles("INVENTORY_STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock alerts")))
                .andExpect(content().string(containsString("Inventory status")))
                .andExpect(content().string(not(containsString("Today's sales"))));
    }

    @Test
    void adminSeesManagerSectionsAndUserAccounts() throws Exception {
        mvc.perform(get("/dashboard").with(user("test-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User accounts")))
                .andExpect(content().string(containsString("Today's sales")))
                .andExpect(content().string(containsString("Inventory status")));
    }
}
