package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.service.CommissionService;

import com.quadmlabs.saphikuna.security.AdminPrincipal;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.concurrent.atomic.AtomicBoolean;

/** Datos que el menú lateral muestra en todas las pantallas del panel. */
@ControllerAdvice
public class AdminNavAdvice {

    private static final Logger log = LoggerFactory.getLogger(AdminNavAdvice.class);

    private final ObjectProvider<CommissionService> commissions;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final String storeUrl;

    public AdminNavAdvice(ObjectProvider<CommissionService> commissions,
            @Value("${app.store-url:}") String storeUrl) {
        this.commissions = commissions;
        this.storeUrl = storeUrl;
    }

    /**
     * Número de entregas que piden atención (vencidas, de hoy o de esta semana), para el
     * contador del menú. Si no se puede calcular (por ejemplo, la tabla aún no existe) el
     * panel sigue funcionando sin contador.
     */
    @ModelAttribute("urgentCommissions")
    public long urgentCommissions(HttpServletRequest request) {
        CommissionService service = commissions.getIfAvailable();
        if (service == null || !isAdminPage(request)) {
            return 0;
        }
        try {
            return service.urgentCount();
        } catch (RuntimeException e) {
            if (warned.compareAndSet(false, true)) {
                log.warn("No se pudo contar los encargos urgentes (¿se ejecutó db/004_commissions.sql?): {}",
                        e.getMessage());
            }
            return 0;
        }
    }

    /** Nombre de quien tiene la sesión abierta, para la barra superior. */
    @ModelAttribute("adminName")
    public String adminName(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof AdminPrincipal p ? p.displayName() : authentication.getName();
    }

    /** Enlace a la app de ventas (vacío si no está configurado). */
    @ModelAttribute("storeUrl")
    public String storeUrl() {
        return storeUrl;
    }

    /** Las pantallas públicas (login, catálogo) no consultan nada para el menú. */
    private static boolean isAdminPage(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/admin");
    }
}
