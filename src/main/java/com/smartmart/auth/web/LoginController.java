package com.smartmart.auth.web;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.smartmart.auth.domain.RoleName;

/** Shows the login page and sends a logged-in user to the start page for their role. */
@Controller
class LoginController {

    /** In role order, so a user with several roles starts at the first one's page. */
    private static final Map<RoleName, String> START_PAGES = new EnumMap<>(Map.of(
            RoleName.ADMIN, "/users",
            RoleName.MANAGER, "/products",
            RoleName.CASHIER, "/pos",
            RoleName.INVENTORY_STAFF, "/products"));

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String home(Authentication authentication) {
        return START_PAGES.entrySet().stream()
                .filter(entry -> CurrentUserModel.roleNames(authentication).contains(entry.getKey().name()))
                .findFirst()
                .map(entry -> "redirect:" + entry.getValue())
                .orElseThrow(() -> new AccessDeniedException("No role with a start page"));
    }
}
