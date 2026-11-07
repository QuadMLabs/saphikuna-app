package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.entity.Commission;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Un encargo con su urgencia calculada respecto a "hoy" (para mostrarlo en pantalla). */
public record CommissionRow(Commission commission, long daysLeft, DeliveryUrgency urgency) {

    public static CommissionRow of(Commission c, LocalDate today) {
        return new CommissionRow(
                c,
                ChronoUnit.DAYS.between(today, c.getDueDate()),
                DeliveryUrgency.of(c.getDueDate(), c.isDelivered(), today));
    }

    /** Días de diferencia sin signo (para "vencido hace N días" / "en N días"). */
    public long days() {
        return Math.abs(daysLeft);
    }

    /** Nombre en minúsculas para usarlo como clase CSS: overdue, today, soon, later, done. */
    public String level() {
        return urgency.name().toLowerCase();
    }
}
