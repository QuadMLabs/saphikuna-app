package com.quadmlabs.saphikuna.collab;

import com.quadmlabs.saphikuna.service.ColorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Colaboraciones: la lista de marcas con sus prendas, y los formularios de marca y de prenda. */
@Controller
@RequestMapping("/admin/colaboraciones")
public class CollaborationController {

    private static final Logger log = LoggerFactory.getLogger(CollaborationController.class);

    private static final String LIST = "redirect:/admin/colaboraciones";

    private final CollaborationService service;
    private final ColorService colorService;

    public CollaborationController(CollaborationService service, ColorService colorService) {
        this.service = service;
        this.colorService = colorService;
    }

    // ------------------------------------------------------------------
    // Lista
    // ------------------------------------------------------------------

    @GetMapping
    public String list(Model model) {
        model.addAttribute("brands", service.brands());
        model.addAttribute("activeMenu", "collaborations");
        return "admin/collaborations/list";
    }

    // ------------------------------------------------------------------
    // Marcas
    // ------------------------------------------------------------------

    @GetMapping("/nueva")
    public String newBrand(Model model) {
        model.addAttribute("brandForm", new BrandForm());
        return brandView(model, null);
    }

    @PostMapping
    public String createBrand(@Valid @ModelAttribute("brandForm") BrandForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                service.createBrand(form);
                redirect.addFlashAttribute("flashMessage", "collab.brand.saved");
                return LIST;
            } catch (RuntimeException e) {
                log.error("No se pudo guardar la marca", e);
                result.reject("collab.brand.save.error");
            }
        }
        return brandView(model, null);
    }

    @GetMapping("/{id}/editar")
    public String editBrand(@PathVariable UUID id, Model model) {
        model.addAttribute("brandForm", service.brandForm(id));
        return brandView(model, id);
    }

    @PostMapping("/{id}")
    public String updateBrand(@PathVariable UUID id, @Valid @ModelAttribute("brandForm") BrandForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                service.updateBrand(id, form);
                redirect.addFlashAttribute("flashMessage", "collab.brand.saved");
                return LIST;
            } catch (RuntimeException e) {
                log.error("No se pudo guardar la marca {}", id, e);
                result.reject("collab.brand.save.error");
            }
        }
        return brandView(model, id);
    }

    @PostMapping("/{id}/eliminar")
    public String deleteBrand(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            service.deleteBrand(id);
            redirect.addFlashAttribute("flashMessage", "collab.brand.deleted");
        } catch (RuntimeException e) {
            log.error("No se pudo eliminar la marca {}", id, e);
            redirect.addFlashAttribute("flashError", "collab.brand.delete.error");
        }
        return LIST;
    }

    private String brandView(Model model, UUID brandId) {
        model.addAttribute("brandId", brandId);
        model.addAttribute("activeMenu", "collaborations");
        return "admin/collaborations/brand-form";
    }

    // ------------------------------------------------------------------
    // Prendas
    // ------------------------------------------------------------------

    @GetMapping("/{brandId}/prendas/nueva")
    public String newPiece(@PathVariable UUID brandId, Model model) {
        model.addAttribute("pieceForm", new PieceForm());
        return pieceView(model, brandId, null);
    }

    @PostMapping("/{brandId}/prendas")
    public String createPiece(@PathVariable UUID brandId, @Valid @ModelAttribute("pieceForm") PieceForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {

        validate(form, result, 0);

        if (!result.hasErrors()) {
            try {
                service.createPiece(brandId, form);
                redirect.addFlashAttribute("flashMessage", "collab.piece.saved");
                return LIST;
            } catch (RuntimeException e) {
                log.error("No se pudo guardar la prenda", e);
                result.reject("collab.piece.save.error");
            }
        }
        service.decorate(form, null);
        return pieceView(model, brandId, null);
    }

    @GetMapping("/prendas/{pieceId}/editar")
    public String editPiece(@PathVariable UUID pieceId, Model model) {
        model.addAttribute("pieceForm", service.toPieceForm(pieceId));
        return pieceView(model, service.brandOfPiece(pieceId), pieceId);
    }

    @PostMapping("/prendas/{pieceId}")
    public String updatePiece(@PathVariable UUID pieceId, @Valid @ModelAttribute("pieceForm") PieceForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {

        // El formulario envía una fila por cada foto guardada; el servicio vuelve a validar el máximo
        validate(form, result, form.getImages().size());

        if (!result.hasErrors()) {
            try {
                service.updatePiece(pieceId, form);
                redirect.addFlashAttribute("flashMessage", "collab.piece.saved");
                return LIST;
            } catch (RuntimeException e) {
                log.error("No se pudo guardar la prenda {}", pieceId, e);
                result.reject("collab.piece.save.error");
            }
        }
        service.decorate(form, pieceId);
        return pieceView(model, service.brandOfPiece(pieceId), pieceId);
    }

    @PostMapping("/prendas/{pieceId}/eliminar")
    public String deletePiece(@PathVariable UUID pieceId, RedirectAttributes redirect) {
        try {
            service.deletePiece(pieceId);
            redirect.addFlashAttribute("flashMessage", "collab.piece.deleted");
        } catch (RuntimeException e) {
            log.error("No se pudo eliminar la prenda {}", pieceId, e);
            redirect.addFlashAttribute("flashError", "collab.piece.delete.error");
        }
        return LIST;
    }

    private String pieceView(Model model, UUID brandId, UUID pieceId) {
        model.addAttribute("brandId", brandId);
        model.addAttribute("brandName", service.brandName(brandId));
        model.addAttribute("pieceId", pieceId);
        model.addAttribute("colors", colorService.findAll());
        model.addAttribute("maxImages", PieceForm.MAX_IMAGES);
        model.addAttribute("activeMenu", "collaborations");
        return "admin/collaborations/piece-form";
    }

    /** Reglas que no se pueden expresar con anotaciones: máximo de fotos y que sean imágenes. */
    private void validate(PieceForm form, BindingResult result, int existingImages) {

        long removed = form.getImages().stream().filter(PieceForm.ImageRow::isDelete).count();
        long uploads = form.getNewImages().stream().filter(f -> f != null && !f.isEmpty()).count();

        if (existingImages - removed + uploads > PieceForm.MAX_IMAGES) {
            result.rejectValue("newImages", "collab.piece.validation.images.max");
        }

        for (MultipartFile file : form.getNewImages()) {
            if (file != null && !file.isEmpty()
                    && (file.getContentType() == null || !file.getContentType().startsWith("image/"))) {
                result.rejectValue("newImages", "collab.piece.validation.images.type");
                break;
            }
        }
    }
}
