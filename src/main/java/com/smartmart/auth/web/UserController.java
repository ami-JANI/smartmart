package com.smartmart.auth.web;

import java.util.Arrays;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.service.UserRuleException;
import com.smartmart.auth.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The user list and form pages in templates/users (SCRUM-21). Administrators only. */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
class UserController {

    private static final String LIST_PAGE = "users/list";
    private static final String FORM_PAGE = "users/form";
    private static final String BACK_TO_LIST = "redirect:/users";
    private static final String FORM = "userForm";

    private final UserService userService;

    @GetMapping
    String list(Model model) {
        model.addAttribute("users", userService.list());
        model.addAttribute("activeNav", "users");
        return LIST_PAGE;
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute(FORM, new UserForm());
        addFormData(model, null);
        return FORM_PAGE;
    }

    @PostMapping
    String create(@Valid @ModelAttribute(FORM) UserForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                userService.create(form.toData());
                redirect.addFlashAttribute("message", "User created");
                return BACK_TO_LIST;
            } catch (UserRuleException refusal) {
                errors.rejectValue(refusal.getField(), "user.rule", refusal.getMessage());
            }
        }
        addFormData(model, null);
        return FORM_PAGE;
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(FORM, UserForm.of(userService.get(id)));
        addFormData(model, id);
        return FORM_PAGE;
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id, @Valid @ModelAttribute(FORM) UserForm form, BindingResult errors,
                  Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                userService.update(id, form.toData());
                redirect.addFlashAttribute("message", "User saved");
                return BACK_TO_LIST;
            } catch (UserRuleException refusal) {
                errors.rejectValue(refusal.getField(), "user.rule", refusal.getMessage());
            }
        }
        addFormData(model, id);
        return FORM_PAGE;
    }

    @PostMapping("/{id}/deactivate")
    String deactivate(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            userService.deactivate(id, authentication.getName());
            redirect.addFlashAttribute("message", "User deactivated");
        } catch (UserRuleException refusal) {
            redirect.addFlashAttribute("error", refusal.getMessage());
        }
        return BACK_TO_LIST;
    }

    private void addFormData(Model model, Long userId) {
        model.addAttribute("editing", userId != null);
        model.addAttribute("userId", userId);
        model.addAttribute("allRoles", Arrays.stream(RoleName.values()).map(RoleName::name).toList());
        model.addAttribute("activeNav", "users");
    }
}
