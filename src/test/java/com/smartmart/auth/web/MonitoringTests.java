package com.smartmart.auth.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator access, the Prometheus scrape login and the administrators'
 * monitoring card (SCRUM-59, SCRUM-60, SCRUM-62), against the databases from
 * compose.yaml. Metrics export is off in tests by default, so it is turned on
 * here to check the Prometheus endpoint.
 */
@SpringBootTest(properties = {"smartmart.monitoring.metrics-password=test-scrape",
        "smartmart.monitoring.grafana-url=http://grafana.test:3000"})
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class MonitoringTests {

    private final MockMvc mvc;

    @Autowired
    MonitoringTests(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void healthAndProbesArePublicButShowNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/info")).andExpect(jsonPath("$.app.name").value("SmartMart"));
    }

    @Test
    void administratorsSeeBothDatabasesInTheHealthDetails() throws Exception {
        mvc.perform(get("/actuator/health").with(user("a").roles("ADMIN")))
                .andExpect(jsonPath("$.components.primaryDatabase.status").value("UP"))
                .andExpect(jsonPath("$.components.primaryDatabase.details.inRecovery").value(false))
                .andExpect(jsonPath("$.components.replicaDatabase.status").value("UP"))
                .andExpect(jsonPath("$.components.replicaDatabase.details.inRecovery").value(true));
    }

    @Test
    void metricsAreForAdministratorsOnly() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/actuator/metrics").with(user("m").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("smartmart.sales")));
    }

    @Test
    void prometheusNeedsTheScraperLogin() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "test-scrape")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("smartmart_sales_total")))
                .andExpect(content().string(containsString("smartmart_sales_revenue_taka_total")))
                .andExpect(content().string(containsString("smartmart_stock_alerts_open")))
                .andExpect(content().string(containsString("application=\"smartmart\"")));
    }

    @Test
    void administratorsGetTheHealthCardAndMonitoringLink() throws Exception {
        mvc.perform(get("/").with(user("a").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("System health")))
                .andExpect(content().string(containsString("Replica database")))
                .andExpect(content().string(containsString("href=\"http://grafana.test:3000\"")));
        mvc.perform(get("/").with(user("m").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("System health"))))
                .andExpect(content().string(not(containsString("grafana.test"))));
    }
}
