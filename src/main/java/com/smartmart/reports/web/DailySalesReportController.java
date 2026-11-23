package com.smartmart.reports.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.smartmart.reports.service.DailySalesReportService;

import lombok.RequiredArgsConstructor;

/** The daily sales report, reports/daily.html. Managers only (SecurityConfig). */
@Controller
@RequiredArgsConstructor
class DailySalesReportController {

    private final DailySalesReportService reports;

    @GetMapping("/reports/daily")
    String show(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                Model model) {
        LocalDate today = reports.today();
        LocalDate day = date == null ? today : date;
        if (day.isAfter(today)) {
            model.addAttribute("dateError", "The report date cannot be in the future, so today is shown");
            day = today;
        }
        model.addAttribute("report", reports.report(day));
        model.addAttribute("today", today);
        model.addAttribute("activeNav", "reports");
        return "reports/daily";
    }
}
