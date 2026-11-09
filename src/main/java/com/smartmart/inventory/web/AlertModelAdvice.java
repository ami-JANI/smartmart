package com.smartmart.inventory.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.smartmart.inventory.service.AlertService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Adds the count of open inventory alerts to every page model for inventory staff
 * and managers, for the menu badge (FR-INV-9, SCRUM-34, SCRUM-53).
 * Runs only on GET requests to avoid binding a read-only database connection during
 * form submissions.
 */
@ControllerAdvice
@RequiredArgsConstructor
class AlertModelAdvice {

    private final AlertService alertService;

    @ModelAttribute("openAlertCount")
    Long openAlertCount(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || request == null || !"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INVENTORY_STAFF")
                        || a.getAuthority().equals("ROLE_MANAGER"));
        return allowed ? alertService.openAlertCount() : null;
    }
}
