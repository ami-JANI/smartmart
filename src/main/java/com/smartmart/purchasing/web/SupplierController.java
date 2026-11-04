package com.smartmart.purchasing.web;

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

import com.smartmart.purchasing.service.SupplierNameTakenException;
import com.smartmart.purchasing.service.SupplierService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The supplier list and form pages in templates/purchasing (SCRUM-35). */
@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
class SupplierController {

    private static final String LIST_PAGE = "purchasing/suppliers";
    private static final String FORM_PAGE = "purchasing/supplier-form";
    private static final String BACK_TO_LIST = "redirect:/suppliers";
    private static final String FORM = "supplierForm";
    private static final int MAX_PAGE_SIZE = 100;

    private final SupplierService supplierService;

    @GetMapping
    String list(@RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable requested, Model model) {
        Pageable pageable = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE),
                Sort.by("name", "id"));
        model.addAttribute("page", supplierService.search(q, pageable));
        model.addAttribute("q", q);
        model.addAttribute("activeNav", "suppliers");
        return LIST_PAGE;
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute(FORM, new SupplierForm());
        addFormData(model, null);
        return FORM_PAGE;
    }

    @PostMapping
    String create(@Valid @ModelAttribute(FORM) SupplierForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                supplierService.create(form.toData());
                redirect.addFlashAttribute("message", "Supplier created");
                return BACK_TO_LIST;
            } catch (SupplierNameTakenException refusal) {
                errors.rejectValue("name", "supplier.name.taken", refusal.getMessage());
            }
        }
        addFormData(model, null);
        return FORM_PAGE;
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(FORM, SupplierForm.of(supplierService.get(id)));
        addFormData(model, id);
        return FORM_PAGE;
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @Valid @ModelAttribute(FORM) SupplierForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                supplierService.update(id, form.toData());
                redirect.addFlashAttribute("message", "Supplier saved");
                return BACK_TO_LIST;
            } catch (SupplierNameTakenException refusal) {
                errors.rejectValue("name", "supplier.name.taken", refusal.getMessage());
            }
        }
        addFormData(model, id);
        return FORM_PAGE;
    }

    private void addFormData(Model model, Long supplierId) {
        model.addAttribute("editing", supplierId != null);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("activeNav", "suppliers");
    }
}
