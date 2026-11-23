package com.smartmart.reports.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The daily report renders against the real database for a manager. */
@SpringBootTest
@AutoConfigureMockMvc
class DailySalesReportPageTests {

    private static final RequestPostProcessor MANAGER = user("test-manager").roles("MANAGER");

    private final MockMvc mvc;

    @Autowired
    DailySalesReportPageTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void showsEveryTotalAndTheGeneratedTime() throws Exception {
        mvc.perform(get("/reports/daily").with(MANAGER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Number of sales")))
                .andExpect(content().string(containsString("Gross sales")))
                .andExpect(content().string(containsString("Promotion savings")))
                .andExpect(content().string(containsString("Net sales")))
                .andExpect(content().string(containsString("VAT included in net")))
                .andExpect(content().string(containsString("Generated ")));
    }

    @Test
    void aDayWithoutSalesSaysSo() throws Exception {
        mvc.perform(get("/reports/daily").param("date", "2001-01-01").with(MANAGER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 January 2001")))
                .andExpect(content().string(containsString("No sales on this day.")));
    }

    @Test
    void aFutureDateFallsBackToToday() throws Exception {
        mvc.perform(get("/reports/daily").param("date", LocalDate.now().plusDays(2).toString()).with(MANAGER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be in the future")));
    }

    @Test
    void anInvalidDateIsRefused() throws Exception {
        mvc.perform(get("/reports/daily").param("date", "not-a-date").with(MANAGER))
                .andExpect(status().isBadRequest());
    }
}
