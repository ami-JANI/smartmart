package com.smartmart.reports.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.smartmart.auth.service.UserService;
import com.smartmart.auth.service.UserSummary;
import com.smartmart.reports.service.Dashboard;
import com.smartmart.reports.service.DashboardService;

import lombok.RequiredArgsConstructor;

/**
 * The dashboard, reports/dashboard.html. Every role may open it (SecurityConfig);
 * the page shows only the parts that the user's roles cover.
 */
@Controller
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService dashboards;
    private final UserService users;

    @GetMapping("/dashboard")
    String show(Authentication auth, Model model) {
        Dashboard dashboard = dashboards.dashboard();
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("chart", SalesChart.of(dashboard.last30Days()));
        if (hasRole(auth, "ADMIN")) {
            List<UserSummary> accounts = users.list();
            model.addAttribute("userCount", accounts.size());
            model.addAttribute("activeUserCount", accounts.stream().filter(UserSummary::active).count());
        }
        model.addAttribute("activeNav", "dashboard");
        return "reports/dashboard";
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
