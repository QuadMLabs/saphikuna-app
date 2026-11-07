package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.dto.CommissionForm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionService {

    /** Filtros de estado de la lista. */
    String FILTER_ACTIVE = "active";
    String FILTER_ALL = "all";

    /**
     * Lista ordenada por fecha de entrega. {@code statusFilter}: {@link #FILTER_ACTIVE} (sin
     * entregar, por defecto), {@link #FILTER_ALL} o un estado concreto.
     */
    List<CommissionRow> search(String q, String statusFilter);

    Optional<CommissionRow> find(UUID id);

    CommissionForm toForm(UUID id);

    UUID create(CommissionForm form);

    void update(UUID id, CommissionForm form);

    void delete(UUID id);

    /** Clientes ya usados, para sugerirlos en el formulario. */
    List<String> clientNames();

    /** Cuántos encargos piden atención ya (vencidos, de hoy o de los próximos 7 días). */
    long urgentCount();

    /** Entregas para el Home: vencidas, de esta semana y de los próximos 30 días. */
    DeliverySummary deliveries();

    record DeliverySummary(List<CommissionRow> overdue, List<CommissionRow> thisWeek, List<CommissionRow> later,
            long activeCount) {

        public boolean isEmpty() {
            return overdue.isEmpty() && thisWeek.isEmpty() && later.isEmpty();
        }

        public long urgentCount() {
            return overdue.size() + thisWeek.size();
        }
    }
}
