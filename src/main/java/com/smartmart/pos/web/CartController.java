package com.smartmart.pos.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.pos.domain.CartRuleException;
import com.smartmart.pos.service.CartService;
import com.smartmart.pos.service.WeightRejectedException;

import lombok.RequiredArgsConstructor;

/** The cart screen, pos/cart.html (SCRUM-28, SCRUM-29). */
@Controller
@RequestMapping("/pos")
@RequiredArgsConstructor
class CartController {

    private static final String BACK_TO_CART = "redirect:/pos";

    private final CartService cartService;

    // The list of discounts arrives with SCRUM-30.
    @GetMapping
    String show(Model model) {
        model.addAttribute("cart", cartService.view());
        model.addAttribute("discounts", List.of());
        model.addAttribute("activeNav", "pos");
        return "pos/cart";
    }

    @PostMapping("/cart/items")
    String scan(@RequestParam String barcode, @RequestParam(required = false) BigDecimal weight,
                RedirectAttributes redirect) {
        cartService.scan(barcode, weight)
                .ifPresent(prompt -> redirect.addFlashAttribute("weightPrompt", prompt));
        return BACK_TO_CART;
    }

    @PostMapping("/cart/items/{lineId}/quantity")
    String changeQuantity(@PathVariable Long lineId, @RequestParam BigDecimal quantity) {
        cartService.changeQuantity(lineId, quantity);
        return BACK_TO_CART;
    }

    @PostMapping("/cart/items/{lineId}/remove")
    String remove(@PathVariable Long lineId) {
        cartService.remove(lineId);
        return BACK_TO_CART;
    }

    @PostMapping("/cart/undo")
    String undo(RedirectAttributes redirect) {
        cartService.undo();
        redirect.addFlashAttribute("message", "Last change undone");
        return BACK_TO_CART;
    }

    @ExceptionHandler(WeightRejectedException.class)
    String weightRefused(WeightRejectedException refusal, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", refusal.getMessage());
        redirect.addFlashAttribute("weightPrompt", refusal.prompt());
        return BACK_TO_CART;
    }

    @ExceptionHandler(CartRuleException.class)
    String refused(CartRuleException refusal, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", refusal.getMessage());
        return BACK_TO_CART;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    String badInput(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Enter a valid number");
        return BACK_TO_CART;
    }
}
