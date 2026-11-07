package com.quadmlabs.saphikuna.service;

import java.time.LocalDate;

/** Revisión rápida de que todo lo que necesita la aplicación está funcionando. */
public interface SystemStatusService {

    /** Peso aproximado de una foto con su miniatura, en MB (principal ~70 KB + miniatura ~12 KB). */
    double ESTIMATED_MB_PER_IMAGE = 0.09;

    /** Cupo de almacenamiento del plan gratuito de Supabase, en MB. */
    double STORAGE_QUOTA_MB = 1024;

    /** Siempre devuelve un resultado: nunca lanza excepciones aunque algo falle. */
    SystemStatus check();

    /**
     * @param dbOk           la base de datos responde
     * @param dbMillis       lo que tardó en responder
     * @param storageOk      Supabase Storage responde
     * @param storageMillis  lo que tardó en responder
     * @param products       total de productos (-1 si no se pudo leer)
     * @param images         total de fotos (-1 si no se pudo leer)
     * @param zone           zona horaria del servidor (de ella depende "hoy" en las fechas de entrega)
     * @param today          fecha de hoy según el servidor
     */
    record SystemStatus(boolean dbOk, long dbMillis, boolean storageOk, long storageMillis,
            long products, long images, String zone, LocalDate today) {

        public boolean allOk() {
            return dbOk && storageOk;
        }

        /** Espacio ocupado por las fotos, estimado (MB). */
        public double estimatedStorageMb() {
            return Math.max(images, 0) * ESTIMATED_MB_PER_IMAGE;
        }

        /** Porcentaje del cupo gratuito que se estima usado. */
        public double estimatedStoragePercent() {
            return estimatedStorageMb() / STORAGE_QUOTA_MB * 100;
        }
    }
}
