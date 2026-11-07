package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.catalog.CatalogAdapter;
import com.quadmlabs.saphikuna.catalog.CatalogForm;
import com.quadmlabs.saphikuna.catalog.CatalogItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administración de los catálogos (colores, tallas, categorías, colecciones).
 * Una sola pantalla de lista y un solo formulario para todos: cambian los campos.
 */
@Controller
@RequestMapping("/admin")
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    /** Solo estas rutas; "products" lo atiende ProductController. */
    private static final String CATALOGS = "{catalog:colors|sizes|categories|collections}";

    private final Map<String, CatalogAdapter> catalogs = new LinkedHashMap<>();
    private final MessageSource messages;

    public CatalogController(List<CatalogAdapter> adapters, MessageSource messages) {
        adapters.forEach(a -> catalogs.put(a.key(), a));
        this.messages = messages;
    }

    // ------------------------------------------------------------------
    // Lista
    // ------------------------------------------------------------------

    @GetMapping("/" + CATALOGS)
    public String list(@PathVariable String catalog, @RequestParam(required = false) String q, Model model) {

        CatalogAdapter adapter = adapter(catalog);
        List<CatalogItem> items = adapter.list(q);

        model.addAttribute("catalog", adapter);
        model.addAttribute("items", items);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("activeMenu", adapter.key());
        return "admin/catalog/list";
    }

    // ------------------------------------------------------------------
    // Nuevo
    // ------------------------------------------------------------------

    @GetMapping("/" + CATALOGS + "/new")
    public String newItem(@PathVariable String catalog, Model model) {

        CatalogAdapter adapter = adapter(catalog);
        CatalogForm form = new CatalogForm();
        form.getValues().putAll(adapter.defaults());

        return form(model, adapter, form, null, Map.of());
    }

    @PostMapping("/" + CATALOGS)
    public String create(
            @PathVariable String catalog,
            @ModelAttribute("catalogForm") CatalogForm form,
            Model model,
            RedirectAttributes redirect) {

        CatalogAdapter adapter = adapter(catalog);
        Map<String, String> errors = adapter.save(null, form.getValues());

        if (errors.isEmpty()) {
            redirect.addFlashAttribute("flashText", text("catalog.saved"));
            return "redirect:/admin/" + catalog;
        }
        return form(model, adapter, form, null, errors);
    }

    // ------------------------------------------------------------------
    // Editar
    // ------------------------------------------------------------------

    @GetMapping("/" + CATALOGS + "/{id}/edit")
    public String edit(@PathVariable String catalog, @PathVariable UUID id, Model model) {

        CatalogAdapter adapter = adapter(catalog);
        CatalogItem item = adapter.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        CatalogForm form = new CatalogForm();
        form.getValues().putAll(item.values());

        return form(model, adapter, form, item, Map.of());
    }

    @PostMapping("/" + CATALOGS + "/{id}")
    public String update(
            @PathVariable String catalog,
            @PathVariable UUID id,
            @ModelAttribute("catalogForm") CatalogForm form,
            Model model,
            RedirectAttributes redirect) {

        CatalogAdapter adapter = adapter(catalog);
        CatalogItem item = adapter.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Map<String, String> errors = adapter.save(id, form.getValues());

        if (errors.isEmpty()) {
            redirect.addFlashAttribute("flashText", text("catalog.saved"));
            return "redirect:/admin/" + catalog;
        }
        return form(model, adapter, form, item, errors);
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @PostMapping("/" + CATALOGS + "/{id}/delete")
    public String delete(@PathVariable String catalog, @PathVariable UUID id, RedirectAttributes redirect) {

        CatalogAdapter adapter = adapter(catalog);

        try {
            CatalogAdapter.DeleteResult result = adapter.delete(id);

            if (result.deleted()) {
                redirect.addFlashAttribute("flashText", text("catalog.deleted"));
            } else {
                // Está en uso: no se borra para no dejar productos sin color/talla/categoría
                redirect.addFlashAttribute("flashError",
                        text("catalog.delete.inuse", result.name(), result.usage()));
            }
        } catch (DataIntegrityViolationException e) {
            log.error("No se pudo eliminar {} {}", catalog, id, e);
            redirect.addFlashAttribute("flashError", text("catalog.delete.error"));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", text("catalog.delete.notfound"));
        }
        return "redirect:/admin/" + catalog;
    }

    // ------------------------------------------------------------------

    private String form(Model model, CatalogAdapter adapter, CatalogForm form, CatalogItem item,
            Map<String, String> errors) {

        model.addAttribute("catalog", adapter);
        model.addAttribute("fields", adapter.fields());
        model.addAttribute("catalogForm", form);
        model.addAttribute("item", item);
        model.addAttribute("errors", errors);
        model.addAttribute("activeMenu", adapter.key());
        return "admin/catalog/form";
    }

    private CatalogAdapter adapter(String key) {
        CatalogAdapter adapter = catalogs.get(key);
        if (adapter == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return adapter;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
