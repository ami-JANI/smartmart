package com.smartmart.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Shows the login page, and the home page every user reaches after logging in
 * (FR-AUTH-1). The home page, templates/home.html, shows one card for each part
 * of SmartMart the user's roles may open, using the global roles attribute.
 */
@Controller
class LoginController {

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("activeNav", "home");
        return "home";
    }
}
