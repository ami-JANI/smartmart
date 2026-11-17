package com.smartmart.pos.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.auth.service.AccountDetails;
import com.smartmart.inventory.service.InsufficientStockException;
import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.service.CartService;
import com.smartmart.pos.service.CheckoutFacade;
import com.smartmart.pos.service.PaymentDetails;
import com.smartmart.pos.service.PaymentRefusedException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The cash payment page, pos/checkout.html (SCRUM-31, SCRUM-46). It only reads
 * the form and calls {@link CheckoutFacade}; a completed sale goes to its
 * invoice page.
 */
@Controller
@RequestMapping("/pos/checkout")
@RequiredArgsConstructor
class CheckoutController {

    private static final String PAGE = "pos/checkout";
    private static final String FORM = "checkoutForm";

    private final CartService cartService;
    private final CheckoutFacade checkout;

    @GetMapping
    String show(Model model, RedirectAttributes redirect) {
        if (cartService.view().lines().isEmpty()) {
            redirect.addFlashAttribute("error", "The cart is empty");
            return "redirect:/pos";
        }
        model.addAttribute(FORM, new CheckoutForm());
        return page(model);
    }

    @PostMapping
    String complete(@Valid @ModelAttribute(FORM) CheckoutForm form, BindingResult errors,
                    @AuthenticationPrincipal AccountDetails cashier, Model model,
                    HttpServletResponse response, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return page(model);
        }
        try {
            Long saleId = checkout.completeSale(cashier.getId(), new PaymentDetails(form.getCashReceived()));
            return "redirect:/pos/invoices/" + saleId;
        } catch (PaymentRefusedException refused) {
            errors.rejectValue("cashReceived", "refused", refused.getMessage());
            return page(model);
        } catch (InsufficientStockException refused) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute("stockError", refused.getMessage());
            return page(model);
        } catch (CartRuleException refused) {
            redirect.addFlashAttribute("error", refused.getMessage());
            return "redirect:/pos";
        }
    }

    private String page(Model model) {
        model.addAttribute("cart", cartService.view());
        model.addAttribute("activeNav", "pos");
        return PAGE;
    }
}
