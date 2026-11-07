package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.CommissionForm;
import com.quadmlabs.saphikuna.entity.Commission;
import com.quadmlabs.saphikuna.repository.CommissionRepository;
import com.quadmlabs.saphikuna.service.CommissionRow;
import com.quadmlabs.saphikuna.service.CommissionService;
import com.quadmlabs.saphikuna.service.DeliveryUrgency;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommissionServiceImpl implements CommissionService {

    private static final int LATER_DAYS = 30;

    private final CommissionRepository repository;
    private final Clock clock;

    public CommissionServiceImpl(CommissionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CommissionRow> search(String q, String statusFilter) {

        Specification<Commission> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            if (statusFilter == null || statusFilter.isBlank() || FILTER_ACTIVE.equals(statusFilter)) {
                where.add(cb.notEqual(root.get("status"), Commission.DELIVERED));
            } else if (!FILTER_ALL.equals(statusFilter)) {
                where.add(cb.equal(root.get("status"), statusFilter));
            }

            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("clientName")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("title")), like, '\\')));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };

        LocalDate today = today();
        return repository.findAll(spec, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("clientName")))
                .stream().map(c -> CommissionRow.of(c, today)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommissionRow> find(UUID id) {
        LocalDate today = today();
        return repository.findById(id).map(c -> CommissionRow.of(c, today));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> clientNames() {
        return repository.findClientNames();
    }

    @Override
    @Transactional(readOnly = true)
    public long urgentCount() {
        return repository.countByStatusNotAndDueDateLessThanEqual(
                Commission.DELIVERED, today().plusDays(DeliveryUrgency.SOON_DAYS));
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverySummary deliveries() {

        LocalDate today = today();
        List<CommissionRow> overdue = new ArrayList<>();
        List<CommissionRow> thisWeek = new ArrayList<>();
        List<CommissionRow> later = new ArrayList<>();

        for (Commission c : repository.findByStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(
                Commission.DELIVERED, today.plusDays(LATER_DAYS))) {

            CommissionRow row = CommissionRow.of(c, today);
            switch (row.urgency()) {
                case OVERDUE -> overdue.add(row);
                case TODAY, SOON -> thisWeek.add(row);
                default -> later.add(row);
            }
        }
        return new DeliverySummary(overdue, thisWeek, later, repository.countByStatusNot(Commission.DELIVERED));
    }

    // ------------------------------------------------------------------
    // Formularios
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CommissionForm toForm(UUID id) {
        Commission c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Encargo no encontrado"));

        CommissionForm f = new CommissionForm();
        f.setClientName(c.getClientName());
        f.setTitle(c.getTitle());
        f.setDescription(c.getDescription());
        f.setQuantity(c.getQuantity());
        f.setDueDate(c.getDueDate());
        f.setStatus(c.getStatus());
        f.setAgreedPrice(c.getAgreedPrice());
        f.setAdvancePaid(c.getAdvancePaid());
        f.setNotes(c.getNotes());
        return f;
    }

    // ------------------------------------------------------------------
    // Guardar / borrar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UUID create(CommissionForm form) {
        Commission c = new Commission();
        c.setCommissionId(UUID.randomUUID());
        c.setCreatedAt(OffsetDateTime.now(clock));
        apply(c, form);
        return repository.save(c).getCommissionId();
    }

    @Override
    @Transactional
    public void update(UUID id, CommissionForm form) {
        Commission c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Encargo no encontrado"));
        apply(c, form);
        repository.save(c);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private void apply(Commission c, CommissionForm f) {
        c.setClientName(f.getClientName().trim());
        c.setTitle(f.getTitle().trim());
        c.setDescription(blankToNull(f.getDescription()));
        c.setQuantity(f.getQuantity());
        c.setDueDate(f.getDueDate());
        c.setStatus(f.getStatus() != null && Commission.STATUSES.contains(f.getStatus()) ? f.getStatus() : Commission.TO_START);
        c.setAgreedPrice(f.getAgreedPrice());
        c.setAdvancePaid(f.getAdvancePaid() == null ? BigDecimal.ZERO : f.getAdvancePaid());
        c.setNotes(blankToNull(f.getNotes()));
        c.setUpdatedAt(OffsetDateTime.now(clock));
    }

    // ------------------------------------------------------------------

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String escapeLike(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
