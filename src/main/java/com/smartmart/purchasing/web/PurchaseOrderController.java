package com.smartmart.purchasing.web;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.inventory.service.ProductNotFoundException;
import com.smartmart.inventory.service.ProductRuleException;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.purchasing.domain.IllegalStateTransitionException;
import com.smartmart.purchasing.domain.PurchaseOrderRuleException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.service.PurchaseOrderDetail;
import com.smartmart.purchasing.service.PurchaseOrderLineView;
import com.smartmart.purchasing.service.PurchaseOrderService;
import com.smartmart.purchasing.service.SupplierNotFoundException;
import com.smartmart.purchasing.service.SupplierService;
import com.smartmart.purchasing.service.SupplierSummary;

import lombok.RequiredArgsConstructor;

/**
 * Purchase order pages in templates/purchasing (SCRUM-54). Inventory staff raise
 * drafts and receive goods; managers submit, cancel and close (SecurityConfig).
 */
@Controller
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
class PurchaseOrderController {

    private static final String LIST_PAGE = "purchasing/purchase-orders";
    private static final String FORM_PAGE = "purchasing/purchase-order-form";
    private static final String ORDER_PAGE = "purchasing/purchase-order";
    private static final String RECEIVE_PAGE = "purchasing/purchase-order-receive";
    private static final String FORM = "purchaseOrderForm";
    private static final String DELIVERY = "deliveryForm";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int ALL = 1000;

    private final PurchaseOrderService orders;
    private final SupplierService suppliers;
    private final ProductService products;

    @GetMapping
    String list(@RequestParam(required = false) PurchaseOrderStatus status,
                @PageableDefault(size = 20) Pageable requested, Model model) {
        // Newest first; the service sets the order.
        Pageable pageable = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE));
        model.addAttribute("page", orders.search(status, pageable));
        model.addAttribute("status", status);
        model.addAttribute("statuses", PurchaseOrderStatus.values());
        model.addAttribute("activeNav", "purchase-orders");
        return LIST_PAGE;
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute(FORM, PurchaseOrderForm.empty());
        addFormData(model);
        return FORM_PAGE;
    }

    /** Saves the draft, or with {@code addRows} only shows more empty rows. */
    @PostMapping
    String create(@ModelAttribute(FORM) PurchaseOrderForm form, BindingResult errors,
                  @RequestParam(required = false) String addRows, Model model, RedirectAttributes redirect) {
        if (addRows != null) {
            form.addRows(PurchaseOrderForm.START_ROWS);
        } else if (!errors.hasErrors()) {
            try {
                Long id = orders.create(form.toData());
                redirect.addFlashAttribute("message", "Draft purchase order raised");
                return "redirect:/purchase-orders/" + id;
            } catch (PurchaseOrderRuleException refusal) {
                if (refusal.getField().equals("supplierId") || refusal.getField().equals("note")) {
                    errors.rejectValue(refusal.getField(), "purchaseOrder.rule", refusal.getMessage());
                } else {
                    errors.reject("purchaseOrder.rule", refusal.getMessage());
                }
            } catch (SupplierNotFoundException refusal) {
                errors.rejectValue("supplierId", "purchaseOrder.supplier", "Choose an active supplier");
            } catch (ProductNotFoundException refusal) {
                errors.reject("purchaseOrder.product", "One of the products no longer exists");
            }
        }
        addFormData(model);
        return FORM_PAGE;
    }

    @GetMapping("/{id}")
    String show(@PathVariable Long id, Model model) {
        model.addAttribute("order", orders.get(id));
        model.addAttribute("activeNav", "purchase-orders");
        return ORDER_PAGE;
    }

    @PostMapping("/{id}/submit")
    String submit(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, () -> orders.submit(id), "Purchase order submitted", redirect);
    }

    @PostMapping("/{id}/cancel")
    String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, () -> orders.cancel(id), "Purchase order cancelled", redirect);
    }

    @PostMapping("/{id}/close")
    String close(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, () -> orders.close(id), "Purchase order closed", redirect);
    }

    @GetMapping("/{id}/receive")
    String receiveForm(@PathVariable Long id, Model model) {
        PurchaseOrderDetail order = orders.get(id);
        model.addAttribute(DELIVERY, DeliveryForm.of(order));
        return receivePage(order, model);
    }

    @PostMapping("/{id}/receive")
    String receive(@PathVariable Long id, @ModelAttribute(DELIVERY) DeliveryForm form, BindingResult errors,
                   Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                orders.receive(id, form.toData());
                redirect.addFlashAttribute("message", "Delivery received into stock");
                return "redirect:/purchase-orders/" + id;
            } catch (PurchaseOrderRuleException | ProductRuleException | IllegalStateTransitionException refusal) {
                errors.reject("delivery.rule", refusal.getMessage());
            }
        }
        return receivePage(orders.get(id), model);
    }

    private String receivePage(PurchaseOrderDetail order, Model model) {
        model.addAttribute("order", order);
        model.addAttribute("orderLines", order.lines().stream()
                .collect(Collectors.toMap(PurchaseOrderLineView::lineId, Function.identity())));
        model.addAttribute("activeNav", "purchase-orders");
        return RECEIVE_PAGE;
    }

    private String act(Long id, Runnable action, String done, RedirectAttributes redirect) {
        try {
            action.run();
            redirect.addFlashAttribute("message", done);
        } catch (IllegalStateTransitionException refusal) {
            redirect.addFlashAttribute("error", refusal.getMessage());
        }
        return "redirect:/purchase-orders/" + id;
    }

    // Active suppliers and products only, by name, since only those can go on a new order.
    private void addFormData(Model model) {
        model.addAttribute("suppliers", suppliers.search(null, PageRequest.of(0, ALL, Sort.by("name"))).getContent()
                .stream().filter(SupplierSummary::active).toList());
        model.addAttribute("products", products.search(null, null, false, PageRequest.of(0, ALL, Sort.by("name")))
                .getContent().stream().filter(ProductSummary::active).toList());
        model.addAttribute("activeNav", "purchase-orders");
    }
}
