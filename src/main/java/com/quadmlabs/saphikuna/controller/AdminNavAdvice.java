package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.service.CommissionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.concurrent.atomic.AtomicBoolean;

/** Datos que el menú lateral muestra en todas las pantallas del panel. */
@ControllerAdvice
public class AdminNavAdvice {

    private static final Logger log = LoggerFactory.getLogger(AdminNavAdvice.class);

    private final ObjectProvider<CommissionService> commissions;
    private final AtomicBoolean warned = new AtomicBoolean();

    public AdminNavAdvice(ObjectProvider<CommissionService> commissions) {
        this.commissions = commissions;
    }

    /**
     * Número de entregas que piden atención (vencidas, de hoy o de esta semana), para el
     * contador del menú. Si no se puede calcular (por ejemplo, la tabla aún no existe) el
     * panel sigue funcionando sin contador.
     */
    @ModelAttribute("urgentCommissions")
    public long urgentCommissions() {
        CommissionService service = commissions.getIfAvailable();
        if (service == null) {
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
}
