package com.smartmart.reports.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.smartmart.reports.service.Dashboard;
import com.smartmart.reports.service.DashboardService;

import lombok.RequiredArgsConstructor;

/** The manager dashboard, reports/dashboard.html. Managers only (SecurityConfig). */
@Controller
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService dashboards;

    @GetMapping("/dashboard")
    String show(Model model) {
        Dashboard dashboard = dashboards.dashboard();
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("chart", SalesChart.of(dashboard.last30Days()));
        model.addAttribute("activeNav", "dashboard");
        return "reports/dashboard";
    }
}
