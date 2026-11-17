package com.smartmart.pos.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.pos.service.CartService;
import com.smartmart.pos.service.CustomerRuleException;
import com.smartmart.pos.service.CustomerService;

import lombok.RequiredArgsConstructor;

/** Members registered by phone at the till, pos/members.html. Cashiers only (under /pos). */
@Controller
@RequestMapping("/pos/members")
@RequiredArgsConstructor
class MemberController {

    private static final String PAGE = "pos/members";

    private final CustomerService customers;
    private final CartService cartService;

    @GetMapping
    String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("members", customers.search(q));
        model.addAttribute("activeNav", "members");
        return PAGE;
    }

    /** Registers a member; with addToSale, also adds them to the current sale and returns to the cart. */
    @PostMapping
    String register(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String phone,
                    @RequestParam(defaultValue = "false") boolean addToSale, Model model, RedirectAttributes redirect) {
        try {
            customers.register(name, phone);
        } catch (CustomerRuleException refused) {
            model.addAttribute(refused.field() + "Error", refused.getMessage());
            model.addAttribute("name", name);
            model.addAttribute("phone", phone);
            model.addAttribute("addToSale", addToSale);
            return list(null, model);
        }
        if (addToSale) {
            cartService.addMember(phone);
            redirect.addFlashAttribute("message", "Member registered and added to the sale");
            return "redirect:/pos";
        }
        redirect.addFlashAttribute("message", "Member registered");
        return "redirect:/pos/members";
    }
}
