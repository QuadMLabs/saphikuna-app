package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.AdminForm;
import com.quadmlabs.saphikuna.dto.ProfileForm;
import com.quadmlabs.saphikuna.entity.Admin;
import com.quadmlabs.saphikuna.security.AdminAccountService;
import com.quadmlabs.saphikuna.security.AdminAccountService.AccountException;
import com.quadmlabs.saphikuna.security.AdminPrincipal;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Administradores del panel (/admin/users) y "Mi perfil" (/admin/profile). */
@Controller
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final AdminAccountService accounts;
    private final MessageSource messages;
    private final Clock clock;

    public AdminUserController(AdminAccountService accounts, MessageSource messages, Clock clock) {
        this.accounts = accounts;
        this.messages = messages;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Lista
    // ------------------------------------------------------------------

    @GetMapping("/admin/users")
    public String list(@AuthenticationPrincipal Object principal, Model model) {
        model.addAttribute("admins", accounts.findAll());
        model.addAttribute("now", OffsetDateTime.now(clock));
        model.addAttribute("meId", currentId(principal));
        model.addAttribute("activeMenu", "users");
        return "admin/users/list";
    }

    // ------------------------------------------------------------------
    // Nuevo
    // ------------------------------------------------------------------

    @GetMapping("/admin/users/new")
    public String newAdmin(Model model) {
        return form(model, new AdminForm(), null);
    }

    @PostMapping("/admin/users")
    public String create(
            @Valid @ModelAttribute("adminForm") AdminForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        validatePassword(form.getPassword(), form.getConfirmPassword(), true, "password", "confirmPassword", result);

        if (!result.hasErrors()) {
            try {
                accounts.create(form.getUsername(), form.getDisplayName(), form.getPassword());
                redirect.addFlashAttribute("flashText", text("admins.saved"));
                return "redirect:/admin/users";
            } catch (AccountException e) {
                reject(result, e);
            } catch (RuntimeException e) {
                log.error("No se pudo crear el administrador", e);
                result.reject("admins.save.error");
            }
        }
        return form(model, form, null);
    }

    // ------------------------------------------------------------------
    // Editar
    // ------------------------------------------------------------------

    @GetMapping("/admin/users/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        Admin admin = accounts.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AdminForm form = new AdminForm();
        form.setUsername(admin.getUsername());
        form.setDisplayName(admin.getDisplayName());
        form.setEnabled(admin.isEnabled());
        return form(model, form, admin);
    }

    @PostMapping("/admin/users/{id}")
    public String update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Object principal,
            @ModelAttribute("adminForm") AdminForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        Admin admin = accounts.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // El usuario no se edita: se conserva el guardado
        form.setUsername(admin.getUsername());
        if (form.getDisplayName() == null || form.getDisplayName().isBlank()) {
            result.rejectValue("displayName", "admins.validation.name.required");
        } else if (form.getDisplayName().length() > 60) {
            result.rejectValue("displayName", "admins.validation.name.size");
        }
        validatePassword(form.getPassword(), form.getConfirmPassword(), false, "password", "confirmPassword", result);

        if (!result.hasErrors()) {
            try {
                accounts.update(id, form.getDisplayName(), form.isEnabled(), form.getPassword(), currentId(principal));
                redirect.addFlashAttribute("flashText", text("admins.saved"));
                return "redirect:/admin/users";
            } catch (AccountException e) {
                reject(result, e);
            } catch (RuntimeException e) {
                log.error("No se pudo guardar el administrador {}", id, e);
                result.reject("admins.save.error");
            }
        }
        return form(model, form, admin);
    }

    // ------------------------------------------------------------------
    // Mi perfil
    // ------------------------------------------------------------------

    @GetMapping("/admin/profile")
    public String profile(@AuthenticationPrincipal Object principal, Model model) {
        ProfileForm form = new ProfileForm();
        form.setDisplayName(principal instanceof AdminPrincipal p ? p.displayName() : "");
        return profileView(model, form, principal);
    }

    @PostMapping("/admin/profile")
    public String saveProfile(
            @AuthenticationPrincipal Object principal,
            @Valid @ModelAttribute("profileForm") ProfileForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        UUID me = currentId(principal);
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        boolean changePassword = notBlank(form.getNewPassword()) || notBlank(form.getCurrentPassword());
        if (changePassword) {
            validatePassword(form.getNewPassword(), form.getConfirmPassword(), true, "newPassword", "confirmPassword", result);
        }

        if (!result.hasErrors()) {
            try {
                accounts.changeOwnName(me, form.getDisplayName());
                if (changePassword) {
                    accounts.changeOwnPassword(me, form.getCurrentPassword(), form.getNewPassword());
                }
                redirect.addFlashAttribute("flashText", text("profile.saved"));
                return "redirect:/admin/profile";
            } catch (AccountException e) {
                reject(result, e);
            } catch (RuntimeException e) {
                log.error("No se pudo guardar el perfil", e);
                result.reject("admins.save.error");
            }
        }
        return profileView(model, form, principal);
    }

    // ------------------------------------------------------------------

    private String form(Model model, AdminForm form, Admin admin) {
        model.addAttribute("adminForm", form);
        model.addAttribute("admin", admin);
        model.addAttribute("minPassword", AdminForm.MIN_PASSWORD);
        model.addAttribute("activeMenu", "users");
        return "admin/users/form";
    }

    private String profileView(Model model, ProfileForm form, Object principal) {
        model.addAttribute("profileForm", form);
        model.addAttribute("username", principal instanceof AdminPrincipal p ? p.username() : "");
        model.addAttribute("minPassword", AdminForm.MIN_PASSWORD);
        model.addAttribute("activeMenu", "profile");
        return "admin/profile";
    }

    /** Clave obligatoria (alta) u opcional (edición); siempre con largo mínimo y confirmación igual. */
    private void validatePassword(String password, String confirm, boolean required,
            String field, String confirmField, BindingResult result) {

        if (!notBlank(password)) {
            if (required) {
                result.rejectValue(field, "admins.validation.password.required");
            }
            return;
        }
        if (password.length() < AdminForm.MIN_PASSWORD) {
            result.rejectValue(field, "admins.validation.password.size", new Object[] { AdminForm.MIN_PASSWORD }, null);
        } else if (!password.equals(confirm)) {
            result.rejectValue(confirmField, "admins.validation.password.mismatch");
        }
    }

    private void reject(BindingResult result, AccountException e) {
        if (e.field() != null && result.getFieldError(e.field()) == null) {
            result.rejectValue(e.field(), e.getMessage());
        } else {
            result.reject(e.getMessage());
        }
    }

    private static UUID currentId(Object principal) {
        return principal instanceof AdminPrincipal p ? p.adminId() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
