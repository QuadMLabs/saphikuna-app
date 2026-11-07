package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.SettingsForm;
import com.quadmlabs.saphikuna.service.SettingsService;

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

/** Configuración de la aplicación. */
@Controller
@RequestMapping("/admin/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settings;
    private final MessageSource messages;

    public SettingsController(SettingsService settings, MessageSource messages) {
        this.settings = settings;
        this.messages = messages;
    }

    @GetMapping
    public String show(Model model) {
        SettingsForm form = new SettingsForm();
        form.setNewProductDays(settings.newProductDays());
        return view(model, form);
    }

    @PostMapping
    public String save(
            @Valid @ModelAttribute("settingsForm") SettingsForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (!result.hasErrors()) {
            try {
                settings.saveNewProductDays(form.getNewProductDays());
                redirect.addFlashAttribute("flashText",
                        messages.getMessage("settings.saved", null, LocaleContextHolder.getLocale()));
                return "redirect:/admin/settings";
            } catch (RuntimeException e) {
                log.error("No se pudo guardar la configuración", e);
                result.reject("settings.save.error");
            }
        }
        return view(model, form);
    }

    private String view(Model model, SettingsForm form) {
        model.addAttribute("settingsForm", form);
        model.addAttribute("activeMenu", "settings");
        return "admin/settings";
    }
}
