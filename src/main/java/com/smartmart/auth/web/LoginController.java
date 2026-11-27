package com.smartmart.auth.web;

import java.util.List;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;

/**
 * Shows the login page, and the home page every user reaches after logging in
 * (FR-AUTH-1). The home page, templates/home.html, shows one card for each part
 * of SmartMart the user's roles may open, using the global roles attribute.
 * Administrators also get the system health card (SCRUM-62).
 */
@Controller
@RequiredArgsConstructor
class LoginController {

    private final HealthEndpoint health;

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home(Authentication auth, Model model) {
        if (CurrentUserModel.roleNames(auth).contains("ADMIN")) {
            model.addAttribute("systemHealth", List.of(
                    check("Application", health.health()),
                    check("Primary database", health.healthForPath("primaryDatabase")),
                    check("Replica database", health.healthForPath("replicaDatabase"))));
        }
        model.addAttribute("activeNav", "home");
        return "home";
    }

    private static HealthCheck check(String name, HealthComponent component) {
        return new HealthCheck(name, component != null && Status.UP.equals(component.getStatus()));
    }

    /** One line of the system health card: a part of the system and whether it is up. */
    record HealthCheck(String name, boolean up) {
    }
}
