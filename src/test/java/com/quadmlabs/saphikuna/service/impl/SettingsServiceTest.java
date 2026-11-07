package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.entity.Setting;
import com.quadmlabs.saphikuna.repository.SettingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ajuste "días que un producto se muestra como nuevo". */
class SettingsServiceTest {

    private SettingRepository repository;
    private SettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(SettingRepository.class);
        service = new SettingsServiceImpl(repository, Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneId.of("UTC")),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    private Setting setting(String value) {
        Setting s = new Setting();
        s.setKey("newProductDays");
        s.setValue(value);
        return s;
    }

    @Test
    void defaultsToFifteenDaysWhenThereIsNoSetting() {
        when(repository.findById("newProductDays")).thenReturn(Optional.empty());
        assertEquals(15, service.newProductDays());
    }

    @Test
    void readsTheStoredValue() {
        when(repository.findById("newProductDays")).thenReturn(Optional.of(setting("30")));
        assertEquals(30, service.newProductDays());
    }

    @Test
    void anInvalidStoredValueFallsBackToTheDefault() {
        for (String bad : new String[] { "abc", "", "0", "-3", "366", "1.5" }) {
            when(repository.findById("newProductDays")).thenReturn(Optional.of(setting(bad)));
            assertEquals(15, service.newProductDays(), "valor guardado: '" + bad + "'");
        }
    }

    @Test
    void ifTheTableCannotBeReadItFallsBackToTheDefaultInsteadOfFailing() {
        when(repository.findById("newProductDays")).thenThrow(new IllegalStateException("la tabla no existe"));
        assertEquals(15, service.newProductDays());
    }

    @Test
    void savesANewValueCreatingTheSettingIfNeeded() {
        when(repository.findById("newProductDays")).thenReturn(Optional.empty());

        service.saveNewProductDays(20);

        ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
        verify(repository).save(saved.capture());
        assertEquals("newProductDays", saved.getValue().getKey());
        assertEquals("20", saved.getValue().getValue());
        assertEquals(true, saved.getValue().getUpdatedAt() != null);
    }

    @Test
    void savingUpdatesTheExistingSetting() {
        Setting existing = setting("15");
        when(repository.findById("newProductDays")).thenReturn(Optional.of(existing));

        service.saveNewProductDays(60);

        assertEquals("60", existing.getValue());
    }

    @Test
    void daysOutsideTheAllowedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.saveNewProductDays(0));
        assertThrows(IllegalArgumentException.class, () -> service.saveNewProductDays(366));
        verify(repository, never()).save(any());
    }
}
