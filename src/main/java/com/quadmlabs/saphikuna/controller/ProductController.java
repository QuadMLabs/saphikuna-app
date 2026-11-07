package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.ProductFilter;
import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.CollectionService;
import com.quadmlabs.saphikuna.service.ColorService;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.SizeService;
import com.quadmlabs.saphikuna.service.StorageService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private static final int PAGE_SIZE = 12;

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CollectionService collectionService;
    private final ColorService colorService;
    private final SizeService sizeService;
    private final StorageService storageService;

    public ProductController(
            ProductService productService,
            CategoryService categoryService,
            CollectionService collectionService,
            ColorService colorService,
            SizeService sizeService,
            StorageService storageService) {

        this.productService = productService;
        this.categoryService = categoryService;
        this.collectionService = collectionService;
        this.colorService = colorService;
        this.sizeService = sizeService;
        this.storageService = storageService;
    }

    // ------------------------------------------------------------------
    // Listado (filtros y paginación en el servidor, sin JS)
    // ------------------------------------------------------------------

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String collectionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String availability,
            @RequestParam(defaultValue = "0") String page,
            Model model) {

        // Los parámetros vienen de la URL: si son inválidos se ignoran (no hay error 400)
        UUID category = parseUuid(categoryId);
        UUID collection = parseUuid(collectionId);
        int pageNumber = Math.max(parseInt(page), 0);

        ProductFilter filter = new ProductFilter(q, category, collection, status, availability);
        // En orden: SPK-001, SPK-002... (los sin código al final) y, a igual código, por fecha de creación
        Sort sort = Sort.by(Sort.Order.asc("code"), Sort.Order.asc("createdAt"));

        Page<Product> products = productService.search(filter, PageRequest.of(pageNumber, PAGE_SIZE, sort));

        // Página fuera de rango (por ejemplo tras borrar productos): mostrar la última
        if (products.getTotalPages() > 0 && pageNumber >= products.getTotalPages()) {
            products = productService.search(filter, PageRequest.of(products.getTotalPages() - 1, PAGE_SIZE, sort));
        }

        // La tabla muestra la miniatura (liviana); al tocarla se abre la foto completa.
        // Se calculan aparte para no modificar las entidades.
        Map<UUID, String> thumbs = new HashMap<>();
        Map<UUID, String> fulls = new HashMap<>();
        products.forEach(p -> {
            if (p.getPrimaryImagePath() != null && !p.getPrimaryImagePath().isBlank()) {
                thumbs.put(p.getProductId(), storageService.getThumbPublicUrl(p.getPrimaryImagePath()));
                fulls.put(p.getProductId(), storageService.getPublicUrl(p.getPrimaryImagePath()));
            }
        });
        model.addAttribute("thumbs", thumbs);
        model.addAttribute("fulls", fulls);

        model.addAttribute("products", products);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("collections", collectionService.findAll());

        // Valores de los filtros (para mantenerlos al paginar)
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("categoryId", category == null ? "" : category.toString());
        model.addAttribute("collectionId", collection == null ? "" : collection.toString());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("availability", availability == null ? "" : availability);
        model.addAttribute("hasFilters", q != null && !q.isBlank() || category != null || collection != null
                || status != null && !status.isBlank() || availability != null && !availability.isBlank());

        menu(model, "products-list");
        return "admin/products/list";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("productForm", productService.newForm());
        formModel(model, null);
        return "admin/products/form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        validate(form, result, null, 0);

        if (!result.hasErrors()) {
            try {
                productService.create(form);
                redirect.addFlashAttribute("flashMessage", "products.form.saved");
                return "redirect:/admin/products";
            } catch (RuntimeException e) {
                reportError(result, e);
            }
        }

        productService.decorate(form, null);
        formModel(model, null);
        return "admin/products/form";
    }

    // ------------------------------------------------------------------
    // Editar
    // ------------------------------------------------------------------

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("productForm", productService.toForm(id));
        formModel(model, id);
        return "admin/products/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        // El formulario envía una fila por cada foto guardada; el servicio vuelve a validar el máximo
        validate(form, result, id, form.getImages().size());

        if (!result.hasErrors()) {
            try {
                productService.update(id, form);
                redirect.addFlashAttribute("flashMessage", "products.form.saved");
                return "redirect:/admin/products";
            } catch (RuntimeException e) {
                reportError(result, e);
            }
        }

        productService.decorate(form, id);
        formModel(model, id);
        return "admin/products/form";
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            productService.delete(id);
            redirect.addFlashAttribute("flashMessage", "products.deleted");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Normalmente: otra tabla todavía referencia al producto (clave foránea)
            log.error("No se pudo eliminar el producto {} (datos relacionados): {}", id, e.getMostSpecificCause().getMessage(), e);
            redirect.addFlashAttribute("flashError", "products.delete.error.related");
        } catch (RuntimeException e) {
            log.error("No se pudo eliminar el producto {}", id, e);
            redirect.addFlashAttribute("flashError", "products.delete.error");
        }
        return "redirect:/admin/products";
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private void formModel(Model model, UUID productId) {
        model.addAttribute("productId", productId);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("collections", collectionService.findAll());
        model.addAttribute("colors", colorService.findAll());
        model.addAttribute("sizes", sizeService.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        (com.quadmlabs.saphikuna.entity.Size z) -> z.getSortOrder() == null ? 0 : z.getSortOrder()))
                .toList());
        menu(model, productId == null ? "products-new" : "products-list");
    }

    private void menu(Model model, String submenu) {
        model.addAttribute("activeMenu", "products");
        model.addAttribute("activeSubmenu", submenu);
    }

    /** Reglas que no se pueden expresar con anotaciones. */
    private void validate(ProductForm form, BindingResult result, UUID productId, int existingImages) {

        long removed = form.getImages().stream().filter(ProductForm.ImageRow::isDelete).count();
        long uploads = form.getNewImages().stream().filter(f -> f != null && !f.isEmpty()).count();

        if (existingImages - removed + uploads > ProductForm.MAX_IMAGES) {
            result.rejectValue("newImages", "products.form.validation.images.max");
        }

        for (MultipartFile file : form.getNewImages()) {
            if (file != null && !file.isEmpty()
                    && (file.getContentType() == null || !file.getContentType().startsWith("image/"))) {
                result.rejectValue("newImages", "products.form.validation.images.type");
                break;
            }
        }

        if ((form.getSpecialSizeId() == null) != (form.getSpecialSizePrice() == null)) {
            result.rejectValue("specialSizePrice", "products.form.validation.special.incomplete");
        }

        boolean specIncomplete = form.getSpecs().stream().anyMatch(s -> !s.isBlank()
                && (s.getName() == null || s.getName().isBlank() || s.getValue() == null || s.getValue().isBlank()));
        if (specIncomplete) {
            result.rejectValue("specs", "products.form.validation.spec.incomplete");
        }
    }

    private void reportError(BindingResult result, RuntimeException e) {
        log.error("No se pudo guardar el producto", e);
        result.reject("products.form.save.error");
    }
}
