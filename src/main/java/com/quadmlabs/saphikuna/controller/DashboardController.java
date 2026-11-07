package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.ProductFilter;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.service.CommissionService;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inicio del panel: entregas, resumen de productos, pendientes por completar y últimos productos. */
@Controller
@RequestMapping("/admin")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private static final int RECENT_PRODUCTS = 5;

    private final ProductService productService;
    private final CommissionService commissionService;
    private final StorageService storageService;

    public DashboardController(
            ProductService productService,
            CommissionService commissionService,
            StorageService storageService) {

        this.productService = productService;
        this.commissionService = commissionService;
        this.storageService = storageService;
    }

    /** Algo que le falta a algunos productos (foto, descripción...). */
    public record AttentionItem(String kind, long count) {
    }

    @GetMapping
    public String dashboard(Model model) {

        model.addAttribute("activeMenu", "home");
        model.addAttribute("stats", productService.stats());

        // Por completar: solo lo que tenga al menos un producto pendiente
        List<AttentionItem> attention = new ArrayList<>();
        for (String kind : ProductFilter.MISSING) {
            long count = productService.countMissing(kind);
            if (count > 0) {
                attention.add(new AttentionItem(kind, count));
            }
        }
        model.addAttribute("attention", attention);

        // Últimos productos, con su miniatura (se calcula aparte para no modificar las entidades)
        List<Product> recent = productService.recent(RECENT_PRODUCTS);
        Map<UUID, String> thumbs = new HashMap<>();
        recent.forEach(p -> {
            if (p.getPrimaryImagePath() != null && !p.getPrimaryImagePath().isBlank()) {
                thumbs.put(p.getProductId(), storageService.getThumbPublicUrl(p.getPrimaryImagePath()));
            }
        });
        model.addAttribute("recent", recent);
        model.addAttribute("thumbs", thumbs);

        // Entregas: si no se pueden leer (p. ej. falta crear la tabla) el Home sigue funcionando
        try {
            model.addAttribute("deliveries", commissionService.deliveries());
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer las entregas (¿se ejecutó db/004_commissions.sql?): {}", e.getMessage());
            model.addAttribute("deliveries", null);
        }

        return "admin/dashboard";
    }
}
