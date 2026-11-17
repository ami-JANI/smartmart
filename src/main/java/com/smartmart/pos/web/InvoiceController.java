package com.smartmart.pos.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.smartmart.pos.service.InvoiceService;
import com.smartmart.pos.service.SaleNotFoundException;

import lombok.RequiredArgsConstructor;

/** The invoice page, pos/invoice.html, shown straight after a sale (SCRUM-32, SCRUM-47). */
@Controller
@RequestMapping("/pos/invoices")
@RequiredArgsConstructor
class InvoiceController {

    private final InvoiceService invoices;

    @GetMapping("/{saleId}")
    String show(@PathVariable Long saleId, Model model) {
        try {
            model.addAttribute("invoice", invoices.invoice(saleId));
        } catch (SaleNotFoundException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing.getMessage());
        }
        model.addAttribute("activeNav", "pos");
        return "pos/invoice";
    }
}
