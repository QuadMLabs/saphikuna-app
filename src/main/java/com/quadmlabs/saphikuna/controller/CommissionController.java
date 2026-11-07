package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.CommissionForm;
import com.quadmlabs.saphikuna.entity.Commission;
import com.quadmlabs.saphikuna.service.CommissionRow;
import com.quadmlabs.saphikuna.service.CommissionService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Encargos: obras por pedido para marcas o empresas, con fecha de entrega. */
@Controller
@RequestMapping("/admin/commissions")
public class CommissionController {

    private static final Logger log = LoggerFactory.getLogger(CommissionController.class);

    private final CommissionService service;
    private final MessageSource messages;

    public CommissionController(CommissionService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    // ------------------------------------------------------------------
    // Lista
    // ------------------------------------------------------------------

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = CommissionService.FILTER_ACTIVE) String status,
            Model model) {

        // Solo se aceptan los filtros conocidos; cualquier otro valor vuelve a "pendientes"
        String filter = CommissionService.FILTER_ALL.equals(status) || status != null && Commission.STATUSES.contains(status)
                ? status : CommissionService.FILTER_ACTIVE;

        model.addAttribute("rows", service.search(q, filter));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("status", filter);
        model.addAttribute("statuses", Commission.STATUSES);
        model.addAttribute("activeMenu", "commissions");
        return "admin/commissions/list";
    }

    // ------------------------------------------------------------------
    // Nuevo
    // ------------------------------------------------------------------

    @GetMapping("/new")
    public String newCommission(Model model) {
        return form(model, new CommissionForm(), null);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("commissionForm") CommissionForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        validate(form, result);

        if (!result.hasErrors()) {
            try {
                service.create(form);
                redirect.addFlashAttribute("flashText", text("commissions.saved"));
                return "redirect:/admin/commissions";
            } catch (RuntimeException e) {
                log.error("No se pudo guardar el encargo", e);
                result.reject("commissions.save.error");
            }
        }
        return form(model, form, null);
    }

    // ------------------------------------------------------------------
    // Editar
    // ------------------------------------------------------------------

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        CommissionRow row = service.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return form(model, service.toForm(id), row);
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("commissionForm") CommissionForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        CommissionRow row = service.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validate(form, result);

        if (!result.hasErrors()) {
            try {
                service.update(id, form);
                redirect.addFlashAttribute("flashText", text("commissions.saved"));
                return "redirect:/admin/commissions";
            } catch (RuntimeException e) {
                log.error("No se pudo guardar el encargo {}", id, e);
                result.reject("commissions.save.error");
            }
        }
        return form(model, form, row);
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("flashText", text("commissions.deleted"));
        } catch (RuntimeException e) {
            log.error("No se pudo eliminar el encargo {}", id, e);
            redirect.addFlashAttribute("flashError", text("commissions.delete.error"));
        }
        return "redirect:/admin/commissions";
    }

    // ------------------------------------------------------------------

    private String form(Model model, CommissionForm form, CommissionRow row) {
        model.addAttribute("commissionForm", form);
        model.addAttribute("row", row);
        model.addAttribute("statuses", Commission.STATUSES);
        model.addAttribute("clients", service.clientNames());
        model.addAttribute("activeMenu", "commissions");
        return "admin/commissions/form";
    }

    /** Reglas que no se pueden expresar con anotaciones. */
    private void validate(CommissionForm form, BindingResult result) {
        if (form.getStatus() == null || !Commission.STATUSES.contains(form.getStatus())) {
            result.rejectValue("status", "commissions.validation.status.invalid");
        }
        if (form.getAgreedPrice() != null && form.getAdvancePaid() != null
                && form.getAdvancePaid().compareTo(form.getAgreedPrice()) > 0) {
            result.rejectValue("advancePaid", "commissions.validation.advance.exceeds");
        }
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
