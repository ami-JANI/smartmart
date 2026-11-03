package com.smartmart.inventory.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.inventory.service.CategoryRuleException;
import com.smartmart.inventory.service.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The category tree page, inventory/categories.html (SCRUM-23). */
@Controller
@RequestMapping("/categories")
@RequiredArgsConstructor
class CategoryController {

    private static final String PAGE = "inventory/categories";
    private static final String BACK_TO_PAGE = "redirect:/categories";

    private final CategoryService categoryService;

    @GetMapping
    String show(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        addPageData(model);
        return PAGE;
    }

    @PostMapping
    String add(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult errors,
               Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            addPageData(model);
            return PAGE;
        }
        categoryService.create(form.getName(), form.getParentId());
        redirect.addFlashAttribute("message", "Category added");
        return BACK_TO_PAGE;
    }

    @PostMapping("/{id}/rename")
    String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirect) {
        categoryService.rename(id, name);
        redirect.addFlashAttribute("message", "Category renamed");
        return BACK_TO_PAGE;
    }

    @PostMapping("/{id}/move")
    String move(@PathVariable Long id, @RequestParam(required = false) Long parentId, RedirectAttributes redirect) {
        categoryService.move(id, parentId);
        redirect.addFlashAttribute("message", "Category moved");
        return BACK_TO_PAGE;
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        categoryService.delete(id);
        redirect.addFlashAttribute("message", "Category deleted");
        return BACK_TO_PAGE;
    }

    @ExceptionHandler(CategoryRuleException.class)
    String refused(CategoryRuleException refusal, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", refusal.getMessage());
        return BACK_TO_PAGE;
    }

    private void addPageData(Model model) {
        model.addAttribute("categories", categoryService.tree());
        model.addAttribute("parentOptions", categoryService.options());
        model.addAttribute("activeNav", "categories");
    }
}
