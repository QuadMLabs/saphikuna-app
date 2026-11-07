package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.CommissionForm;
import com.quadmlabs.saphikuna.entity.Commission;
import com.quadmlabs.saphikuna.repository.CommissionRepository;
import com.quadmlabs.saphikuna.service.CommissionRow;
import com.quadmlabs.saphikuna.service.CommissionService;
import com.quadmlabs.saphikuna.service.DeliveryUrgency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fechas de entrega, urgencia y avisos de los encargos (con un reloj fijo: "hoy" = 15 de noviembre de 2026). */
class CommissionServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private CommissionRepository repository;
    private CommissionServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CommissionRepository.class);
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.of("America/Guayaquil")).toInstant(),
                ZoneId.of("America/Guayaquil"));
        service = new CommissionServiceImpl(repository, clock);
    }

    private Commission commission(String client, LocalDate due, String status) {
        Commission c = new Commission();
        c.setCommissionId(UUID.randomUUID());
        c.setClientName(client);
        c.setTitle("Trabajo de " + client);
        c.setQuantity(10);
        c.setDueDate(due);
        c.setStatus(status);
        return c;
    }

    // ---------------- urgencia ----------------

    @Test
    void urgencyDependsOnHowCloseTheDeliveryIs() {
        assertEquals(DeliveryUrgency.OVERDUE, DeliveryUrgency.of(TODAY.minusDays(1), false, TODAY));
        assertEquals(DeliveryUrgency.TODAY, DeliveryUrgency.of(TODAY, false, TODAY));
        assertEquals(DeliveryUrgency.SOON, DeliveryUrgency.of(TODAY.plusDays(1), false, TODAY));
        assertEquals(DeliveryUrgency.SOON, DeliveryUrgency.of(TODAY.plusDays(7), false, TODAY));
        assertEquals(DeliveryUrgency.LATER, DeliveryUrgency.of(TODAY.plusDays(8), false, TODAY));
    }

    @Test
    void aDeliveredCommissionIsNeverUrgentEvenIfItsDateHasPassed() {
        assertEquals(DeliveryUrgency.DONE, DeliveryUrgency.of(TODAY.minusDays(30), true, TODAY));
        assertFalse(DeliveryUrgency.DONE.isUrgent());
        assertTrue(DeliveryUrgency.OVERDUE.isUrgent());
        assertTrue(DeliveryUrgency.TODAY.isUrgent());
        assertTrue(DeliveryUrgency.SOON.isUrgent());
        assertFalse(DeliveryUrgency.LATER.isUrgent());
    }

    @Test
    void rowExposesUnsignedDaysAndCssLevel() {
        CommissionRow late = CommissionRow.of(commission("A", TODAY.minusDays(3), Commission.IN_PROGRESS), TODAY);
        assertEquals(-3, late.daysLeft());
        assertEquals(3, late.days());
        assertEquals("overdue", late.level());

        CommissionRow soon = CommissionRow.of(commission("B", TODAY.plusDays(4), Commission.TO_START), TODAY);
        assertEquals(4, soon.days());
        assertEquals("soon", soon.level());
    }

    // ---------------- avisos ----------------

    @Test
    void urgentCountAsksForEverythingDueWithinSevenDaysThatIsNotDelivered() {
        when(repository.countByStatusNotAndDueDateLessThanEqual(any(), any())).thenReturn(4L);

        assertEquals(4, service.urgentCount());
        verify(repository).countByStatusNotAndDueDateLessThanEqual(Commission.DELIVERED, TODAY.plusDays(7));
    }

    @Test
    void deliveriesAreGroupedIntoOverdueThisWeekAndLater() {
        when(repository.findByStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(any(), any())).thenReturn(List.of(
                commission("Vencido", TODAY.minusDays(2), Commission.IN_PROGRESS),
                commission("Hoy", TODAY, Commission.READY),
                commission("En cinco días", TODAY.plusDays(5), Commission.TO_START),
                commission("En veinte días", TODAY.plusDays(20), Commission.TO_START)));
        when(repository.countByStatusNot(Commission.DELIVERED)).thenReturn(9L);

        CommissionService.DeliverySummary s = service.deliveries();

        assertEquals(List.of("Vencido"), s.overdue().stream().map(r -> r.commission().getClientName()).toList());
        assertEquals(List.of("Hoy", "En cinco días"), s.thisWeek().stream().map(r -> r.commission().getClientName()).toList());
        assertEquals(List.of("En veinte días"), s.later().stream().map(r -> r.commission().getClientName()).toList());
        assertEquals(9, s.activeCount());
        assertEquals(3, s.urgentCount());
        assertFalse(s.isEmpty());
        // se piden los próximos 30 días
        verify(repository).findByStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(Commission.DELIVERED, TODAY.plusDays(30));
    }

    @Test
    void noDeliveriesMeansAnEmptySummary() {
        when(repository.findByStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(any(), any())).thenReturn(List.of());
        assertTrue(service.deliveries().isEmpty());
    }

    // ---------------- guardar ----------------

    private CommissionForm form() {
        CommissionForm f = new CommissionForm();
        f.setClientName("  Marca Andina  ");
        f.setTitle(" 40 bufandas ");
        f.setDescription("   ");
        f.setQuantity(40);
        f.setDueDate(TODAY.plusDays(20));
        f.setStatus(Commission.IN_PROGRESS);
        f.setAgreedPrice(new BigDecimal("800.00"));
        f.setAdvancePaid(new BigDecimal("300.00"));
        return f;
    }

    @Test
    void createTrimsTextAndStoresMoney() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(form());

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(repository).save(saved.capture());
        Commission c = saved.getValue();
        assertEquals("Marca Andina", c.getClientName());
        assertEquals("40 bufandas", c.getTitle());
        assertNull(c.getDescription());                       // texto en blanco se guarda como vacío
        assertEquals(TODAY.plusDays(20), c.getDueDate());
        assertEquals(new BigDecimal("500.00"), c.getBalance()); // lo que falta cobrar
        assertTrue(c.getCommissionId() != null && c.getCreatedAt() != null && c.getUpdatedAt() != null);
    }

    @Test
    void anInvalidStatusFallsBackToToStart() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CommissionForm f = form();
        f.setStatus("inventado");

        service.create(f);

        ArgumentCaptor<Commission> saved = ArgumentCaptor.forClass(Commission.class);
        verify(repository).save(saved.capture());
        assertEquals(Commission.TO_START, saved.getValue().getStatus());
    }

    @Test
    void balanceIsNullWithoutAgreedPrice() {
        Commission c = commission("X", TODAY, Commission.TO_START);
        c.setAgreedPrice(null);
        assertNull(c.getBalance());
    }

    @Test
    void searchFiltersAreAppliedAndOrderedByDueDate() {
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(commission("A", TODAY.plusDays(1), Commission.TO_START)));

        List<CommissionRow> rows = service.search("a%", CommissionService.FILTER_ACTIVE);

        assertEquals(1, rows.size());
        ArgumentCaptor<org.springframework.data.domain.Sort> sort = ArgumentCaptor.forClass(org.springframework.data.domain.Sort.class);
        verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class), sort.capture());
        assertEquals(org.springframework.data.domain.Sort.Direction.ASC, sort.getValue().getOrderFor("dueDate").getDirection());
    }

    @Test
    void deleteRemovesTheCommission() {
        UUID id = UUID.randomUUID();
        service.delete(id);
        verify(repository).deleteById(eq(id));
    }
}
