package com.quadmlabs.saphikuna.brochure.builder;

import com.quadmlabs.saphikuna.brochure.BrochurePdfService;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.BuilderException;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.ItemEdit;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.SettingsService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catálogos (/admin/catalogos): lista, datos del catálogo, elegir prendas y la ficha de cada prenda.
 * Pensado para el celular: cada pantalla hace una sola cosa y se avanza de una a la siguiente.
 */
@Controller
@RequestMapping("/admin/catalogos")
public class BrochureBuilderController {

    private static final Logger log = LoggerFactory.getLogger(BrochureBuilderController.class);

    private final BrochureBuilderService builder;
    private final SettingsService settings;
    private final CategoryService categories;
    private final MessageSource messages;
    private final BrochurePdfService pdf;
    private final Clock clock;
    private final String storeUrl;

    public BrochureBuilderController(BrochureBuilderService builder, SettingsService settings,
            CategoryService categories, MessageSource messages, BrochurePdfService pdf, Clock clock,
            @Value("${app.store-url:}") String storeUrl) {
        this.builder = builder;
        this.settings = settings;
        this.categories = categories;
        this.messages = messages;
        this.pdf = pdf;
        this.clock = clock;
        this.storeUrl = storeUrl;
    }

    // ------------------------------------------------------------------
    // Lista
    // ------------------------------------------------------------------

    @GetMapping
    public String list(Model model) {
        model.addAttribute("brochures", builder.list());
        return view(model, "admin/brochures/list");
    }

    // ------------------------------------------------------------------
    // Nuevo
    // ------------------------------------------------------------------

    @GetMapping("/nuevo")
    public String newBrochure(Model model) {
        model.addAttribute("brochureForm", new BrochureForm());
        return view(model, "admin/brochures/new");
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("brochureForm") BrochureForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (!result.hasErrors()) {
            try {
                UUID id = builder.create(form);
                redirect.addFlashAttribute("flashText", text("brochures.created"));
                return "redirect:/admin/catalogos/" + id + "/agregar";
            } catch (RuntimeException e) {
                log.error("No se pudo crear el catálogo", e);
                result.reject("brochures.save.error");
            }
        }
        return view(model, "admin/brochures/new");
    }

    // ------------------------------------------------------------------
    // Catálogo: datos y prendas
    // ------------------------------------------------------------------

    @GetMapping("/{id}")
    public String overview(@PathVariable UUID id, Model model) {
        return overviewView(id, builder.toForm(found(id)), model);
    }

    @PostMapping("/{id}")
    public String save(
            @PathVariable UUID id,
            @Valid @ModelAttribute("brochureForm") BrochureForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (!result.hasErrors()) {
            try {
                builder.update(found(id), form);
                redirect.addFlashAttribute("flashText", text("brochures.saved"));
                return "redirect:/admin/catalogos/" + id;
            } catch (RuntimeException e) {
                log.error("No se pudo guardar el catálogo {}", id, e);
                result.reject("brochures.save.error");
            }
        }
        return overviewView(id, form, model);
    }

    @PostMapping("/{id}/duplicar")
    public String duplicate(@PathVariable UUID id, RedirectAttributes redirect) {
        UUID copy = builder.duplicate(found(id));
        redirect.addFlashAttribute("flashText", text("brochures.duplicated"));
        return "redirect:/admin/catalogos/" + copy;
    }

    @PostMapping("/{id}/eliminar")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            builder.delete(found(id));
            redirect.addFlashAttribute("flashText", text("brochures.deleted"));
        } catch (RuntimeException e) {
            log.error("No se pudo eliminar el catálogo {}", id, e);
            redirect.addFlashAttribute("flashError", text("brochures.delete.error"));
        }
        return "redirect:/admin/catalogos";
    }

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------

    /** El catálogo en PDF: se abre en el navegador, o se descarga con {@code ?descargar=true}. */
    @GetMapping("/{id}/pdf")
    public Object pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean descargar,
            RedirectAttributes redirect) {

        found(id);
        var catalog = builder.pdfData(id);
        if (catalog.items().isEmpty()) {
            redirect.addFlashAttribute("flashError", text("brochures.pdf.empty"));
            return "redirect:/admin/catalogos/" + id;
        }

        byte[] bytes;
        try {
            bytes = pdf.renderCatalog(catalog, settings.storeInfo(), storeUrl, LocalDate.now(clock));
        } catch (RuntimeException e) {
            log.error("No se pudo armar el PDF del catálogo {}", id, e);
            redirect.addFlashAttribute("flashError", text("brochures.pdf.error"));
            return "redirect:/admin/catalogos/" + id;
        }

        ContentDisposition disposition = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName(catalog.name())).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    /** "Catálogo Verano 2026" -> "catalogo-verano-2026.pdf". */
    static String fileName(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return (plain.isEmpty() ? "catalogo" : plain) + ".pdf";
    }

    // ------------------------------------------------------------------
    // Elegir prendas
    // ------------------------------------------------------------------

    @GetMapping("/{id}/agregar")
    public String pick(
            @PathVariable UUID id,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria,
            Model model) {

        UUID categoryId = parseId(categoria);
        model.addAttribute("brochureId", found(id));
        model.addAttribute("brochureName", builder.overview(id).name());
        model.addAttribute("candidates", builder.candidates(id, q, categoryId));
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("categoria", categoryId == null ? "" : categoryId.toString());
        return view(model, "admin/brochures/add");
    }

    @PostMapping("/{id}/agregar")
    public String add(
            @PathVariable UUID id,
            @RequestParam(required = false) List<UUID> ids,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria,
            RedirectAttributes redirect) {

        found(id);
        int added;
        if (all) {
            added = builder.addAll(id, q, parseId(categoria));
        } else if (ids == null || ids.isEmpty()) {
            redirect.addFlashAttribute("flashError", text("brochures.add.none"));
            return "redirect:/admin/catalogos/" + id + "/agregar" + filterQuery(q, categoria);
        } else {
            added = builder.addProducts(id, ids);
        }
        redirect.addFlashAttribute("flashText", added == 0 ? text("brochures.add.nothing") : text("brochures.add.done", added));
        return "redirect:/admin/catalogos/" + id;
    }

    @PostMapping("/{id}/prendas/{itemId}/mover")
    public String move(@PathVariable UUID id, @PathVariable UUID itemId, @RequestParam String dir) {
        builder.moveItem(found(id), itemId, "up".equals(dir) ? -1 : 1);
        return "redirect:/admin/catalogos/" + id + "#prenda-" + itemId;
    }

    @PostMapping("/{id}/prendas/{itemId}/quitar")
    public String remove(@PathVariable UUID id, @PathVariable UUID itemId, RedirectAttributes redirect) {
        builder.removeItem(found(id), itemId);
        redirect.addFlashAttribute("flashText", text("brochures.item.removed"));
        return "redirect:/admin/catalogos/" + id;
    }

    // ------------------------------------------------------------------
    // Ficha de la prenda
    // ------------------------------------------------------------------

    @GetMapping("/{id}/prendas/{itemId}")
    public String item(@PathVariable UUID id, @PathVariable UUID itemId, Model model) {
        return itemView(builder.itemEdit(found(id), itemId), model, null);
    }

    @PostMapping("/{id}/prendas/{itemId}")
    public String saveItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestParam Map<String, String> params,
            Model model,
            RedirectAttributes redirect) {

        found(id);
        ItemForm form = new ItemForm();
        form.setHighlightText(params.get("highlightText"));
        form.setDetailsText(params.get("detailsText"));
        params.forEach((name, value) -> {
            if (name.startsWith("role_")) {
                form.getRoles().put(name.substring("role_".length()), value);
            }
        });

        // "Usar la descripción del producto" / "Restablecer la ficha": no guarda, solo rellena el texto
        String reset = params.get("reset");
        if (reset != null && !reset.isBlank()) {
            builder.resetText(id, itemId, form, reset);
            return itemView(builder.itemEdit(id, itemId, form), model, null, text("brochures.item.reset"));
        }

        try {
            builder.saveItem(id, itemId, form);
        } catch (BuilderException e) {
            return itemView(builder.itemEdit(id, itemId, form), model, text(e.getMessage(), BrochureBuilderService.MAX_EXTRAS));
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la prenda {} del catálogo {}", itemId, id, e);
            return itemView(builder.itemEdit(id, itemId, form), model, text("brochures.save.error"));
        }

        ItemEdit saved = builder.itemEdit(id, itemId);
        if (params.containsKey("next") && saved.nextItemId() != null) {
            redirect.addFlashAttribute("flashText", text("brochures.item.saved"));
            return "redirect:/admin/catalogos/" + id + "/prendas/" + saved.nextItemId();
        }
        redirect.addFlashAttribute("flashText", text("brochures.item.saved"));
        return "redirect:/admin/catalogos/" + id + "#prenda-" + itemId;
    }

    // ------------------------------------------------------------------

    private String overviewView(UUID id, BrochureForm form, Model model) {
        model.addAttribute("brochureForm", form);
        model.addAttribute("ov", builder.overview(id));
        model.addAttribute("store", settings.storeInfo());
        return view(model, "admin/brochures/overview");
    }

    private String itemView(ItemEdit edit, Model model, String photosError) {
        return itemView(edit, model, photosError, null);
    }

    private String itemView(ItemEdit edit, Model model, String error, String notice) {
        model.addAttribute("edit", edit);
        model.addAttribute("error", error);
        model.addAttribute("notice", notice);
        return view(model, "admin/brochures/item");
    }

    private static String view(Model model, String template) {
        model.addAttribute("activeMenu", "brochure");
        return template;
    }

    /** Confirma que el catálogo existe (404 si no) y devuelve su id. */
    private UUID found(UUID id) {
        try {
            builder.overview(id);
            return id;
        } catch (BuilderException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static UUID parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String filterQuery(String q, String categoria) {
        StringBuilder sb = new StringBuilder();
        if (q != null && !q.isBlank()) {
            sb.append(sb.length() == 0 ? "?" : "&").append("q=").append(java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (categoria != null && !categoria.isBlank()) {
            sb.append(sb.length() == 0 ? "?" : "&").append("categoria=").append(java.net.URLEncoder.encode(categoria, java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
