package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.SettingsForm;
import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.service.SettingsService;
import com.quadmlabs.saphikuna.service.SystemStatusService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Configuración: una sola pantalla con tarjetas independientes. Cada tarjeta es su propio
 * formulario con su propio botón Guardar, así guardar una no toca las demás.
 */
@Controller
@RequestMapping("/admin/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settings;
    private final SystemStatusService systemStatus;
    private final MessageSource messages;

    public SettingsController(SettingsService settings, SystemStatusService systemStatus, MessageSource messages) {
        this.settings = settings;
        this.systemStatus = systemStatus;
        this.messages = messages;
    }

    @GetMapping
    public String show(Model model) {
        return view(model, newDaysForm(), settings.storeInfo());
    }

    // ------------------------------------------------------------------
    // Tarjeta: Productos nuevos
    // ------------------------------------------------------------------

    @PostMapping("/new-days")
    public String saveNewDays(
            @Valid @ModelAttribute("newDaysForm") SettingsForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (!result.hasErrors()) {
            try {
                settings.saveNewProductDays(form.getNewProductDays());
                return saved(redirect, "new-days");
            } catch (RuntimeException e) {
                log.error("No se pudo guardar los días de producto nuevo", e);
                result.reject("settings.save.error");
            }
        }
        return view(model, form, settings.storeInfo());
    }

    // ------------------------------------------------------------------
    // Tarjeta: Datos de la tienda
    // ------------------------------------------------------------------

    @PostMapping("/store")
    public String saveStore(
            @Valid @ModelAttribute("storeForm") StoreInfoForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (!result.hasErrors()) {
            try {
                settings.saveStoreInfo(form);
                return saved(redirect, "store");
            } catch (RuntimeException e) {
                log.error("No se pudo guardar los datos de la tienda", e);
                result.reject("settings.save.error");
            }
        }
        return view(model, newDaysForm(), form);
    }

    // ------------------------------------------------------------------

    private SettingsForm newDaysForm() {
        SettingsForm form = new SettingsForm();
        form.setNewProductDays(settings.newProductDays());
        return form;
    }

    /** Vuelve a la misma tarjeta guardada (útil en celular, donde la página es larga). */
    private String saved(RedirectAttributes redirect, String card) {
        redirect.addFlashAttribute("flashText",
                messages.getMessage("settings.saved", null, LocaleContextHolder.getLocale()));
        redirect.addFlashAttribute("flashCard", card);
        return "redirect:/admin/settings#" + card;
    }

    private String view(Model model, SettingsForm newDays, StoreInfoForm store) {
        model.addAttribute("newDaysForm", newDays);
        model.addAttribute("storeForm", store);
        model.addAttribute("status", systemStatus.check());
        model.addAttribute("activeMenu", "settings");
        return "admin/settings";
    }
}
