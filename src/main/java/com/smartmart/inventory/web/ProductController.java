package com.smartmart.inventory.web;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.service.CategoryService;
import com.smartmart.inventory.service.ProductRuleException;
import com.smartmart.inventory.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The product list, form and detail pages in templates/inventory (SCRUM-24). */
@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
class ProductController {

    private static final String LIST_PAGE = "inventory/products";
    private static final String FORM_PAGE = "inventory/product-form";
    private static final String DETAIL_PAGE = "inventory/product-detail";
    private static final String BACK_TO_LIST = "redirect:/products";
    private static final String FORM = "productForm";
    private static final Set<String> SORTABLE = Set.of("name", "barcode", "price");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;
    private final CategoryService categoryService;

    // The low-stock filter only keeps its setting for now; it starts filtering with SCRUM-25.
    @GetMapping
    String list(@RequestParam(required = false) String q,
                @RequestParam(required = false) Long category,
                @RequestParam(required = false) Boolean lowStock,
                @PageableDefault(size = 20) Pageable requested,
                Model model) {
        Sort.Order order = sortOrder(requested);
        Pageable pageable = PageRequest.of(requested.getPageNumber(), Math.min(requested.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(order).and(Sort.by("id")));
        model.addAttribute("page", productService.search(q, category, pageable));
        model.addAttribute("categories", categoryService.options());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("q", q);
        model.addAttribute("lowStockOnly", Boolean.TRUE.equals(lowStock));
        model.addAttribute("sortField", order.getProperty());
        model.addAttribute("sortDir", order.getDirection().name().toLowerCase());
        model.addAttribute("activeNav", "products");
        return LIST_PAGE;
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute(FORM, new ProductForm());
        addFormData(model, null);
        return FORM_PAGE;
    }

    @PostMapping
    String create(@Valid @ModelAttribute(FORM) ProductForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                productService.create(form.toData());
                redirect.addFlashAttribute("message", "Product created");
                return BACK_TO_LIST;
            } catch (ProductRuleException refusal) {
                errors.rejectValue(refusal.getField(), "product.rule", refusal.getMessage());
            }
        }
        addFormData(model, null);
        return FORM_PAGE;
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.get(id));
        // Stock batches arrive with SCRUM-25.
        model.addAttribute("totalStock", BigDecimal.ZERO);
        model.addAttribute("batches", List.of());
        model.addAttribute("activeNav", "products");
        return DETAIL_PAGE;
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(FORM, ProductForm.of(productService.get(id)));
        addFormData(model, id);
        return FORM_PAGE;
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @Valid @ModelAttribute(FORM) ProductForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                productService.update(id, form.toData());
                redirect.addFlashAttribute("message", "Product saved");
                return BACK_TO_LIST;
            } catch (ProductRuleException refusal) {
                errors.rejectValue(refusal.getField(), "product.rule", refusal.getMessage());
            }
        }
        addFormData(model, id);
        return FORM_PAGE;
    }

    private Sort.Order sortOrder(Pageable requested) {
        return requested.getSort().stream()
                .filter(order -> SORTABLE.contains(order.getProperty()))
                .findFirst()
                .orElse(Sort.Order.asc("name"));
    }

    private void addFormData(Model model, Long productId) {
        model.addAttribute("editing", productId != null);
        model.addAttribute("productId", productId);
        model.addAttribute("categories", categoryService.options());
        model.addAttribute("saleUnits", Arrays.stream(SaleUnit.values()).map(SaleUnit::name).toList());
        model.addAttribute("activeNav", "products");
    }
}
