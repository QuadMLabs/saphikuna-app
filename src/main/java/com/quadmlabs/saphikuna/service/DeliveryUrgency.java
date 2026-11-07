package com.quadmlabs.saphikuna.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Qué tan cerca está la entrega de un encargo. */
public enum DeliveryUrgency {

    /** Ya entregado. */
    DONE,
    /** La fecha ya pasó y sigue sin entregarse. */
    OVERDUE,
    /** Se entrega hoy. */
    TODAY,
    /** Dentro de los próximos {@value #SOON_DAYS} días. */
    SOON,
    /** Más adelante. */
    LATER;

    /** Hasta cuántos días antes se considera "pronto". */
    public static final int SOON_DAYS = 7;

    public static DeliveryUrgency of(LocalDate due, boolean delivered, LocalDate today) {
        if (delivered) {
            return DONE;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        if (days < 0) {
            return OVERDUE;
        }
        if (days == 0) {
            return TODAY;
        }
        return days <= SOON_DAYS ? SOON : LATER;
    }

    /** Los que piden atención ya: vencidos, de hoy o de esta semana. */
    public boolean isUrgent() {
        return this == OVERDUE || this == TODAY || this == SOON;
    }
}
