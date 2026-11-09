package com.smartmart.inventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartmart.inventory.service.AlertService;

import lombok.RequiredArgsConstructor;

/**
 * Controller for displaying stock and expiry alerts (FR-INV-8, FR-INV-9, SCRUM-34, SCRUM-53).
 */
@Controller
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("alerts", alertService.openAlerts());
        model.addAttribute("activeNav", "alerts");
        return "inventory/alerts";
    }
}
