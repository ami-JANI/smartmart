package com.smartmart.reports.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The dashboard renders against the real database for a manager. */
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
}
