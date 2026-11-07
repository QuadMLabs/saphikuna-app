package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.entity.Setting;
import com.quadmlabs.saphikuna.repository.SettingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ajustes: días de producto nuevo y datos de la tienda. */
class SettingsServiceTest {

    private SettingRepository repository;
    private SettingsServiceImpl service;
    private final Map<String, Setting> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(SettingRepository.class);
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(stored.values()));
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get((String) inv.getArgument(0))));
        service = new SettingsServiceImpl(repository, Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneId.of("UTC")),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    private void put(String key, String value) {
        Setting s = new Setting();
        s.setKey(key);
        s.setValue(value);
        stored.put(key, s);
    }

    // ---------------- días de producto nuevo ----------------

    @Test
    void defaultsToFifteenDaysWhenThereIsNoSetting() {
        assertEquals(15, service.newProductDays());
    }

    @Test
    void readsTheStoredValue() {
        put("newProductDays", "30");
        assertEquals(30, service.newProductDays());
    }

    @Test
    void anInvalidStoredValueFallsBackToTheDefault() {
        for (String bad : new String[] { "abc", "", "0", "-3", "366", "1.5" }) {
            put("newProductDays", bad);
            assertEquals(15, service.newProductDays(), "valor guardado: '" + bad + "'");
        }
    }

    @Test
    void ifTheTableCannotBeReadItFallsBackToTheDefaultInsteadOfFailing() {
        when(repository.findAll()).thenThrow(new IllegalStateException("la tabla no existe"));

        assertEquals(15, service.newProductDays());
        assertEquals("Saphikuna", service.storeInfo().getStoreName());
    }

    @Test
    void savesANewValueCreatingTheSettingIfNeeded() {
        service.saveNewProductDays(20);

        ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
        verify(repository).save(saved.capture());
        assertEquals("newProductDays", saved.getValue().getKey());
        assertEquals("20", saved.getValue().getValue());
        assertEquals(true, saved.getValue().getUpdatedAt() != null);
    }

    @Test
    void savingUpdatesTheExistingSetting() {
        put("newProductDays", "15");

        service.saveNewProductDays(60);

        assertEquals("60", stored.get("newProductDays").getValue());
    }

    @Test
    void daysOutsideTheAllowedRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.saveNewProductDays(0));
        assertThrows(IllegalArgumentException.class, () -> service.saveNewProductDays(366));
        verify(repository, never()).save(any());
    }

    // ---------------- datos de la tienda ----------------

    @Test
    void storeInfoHasDefaultsForNameAndTaglineAndEmptyContacts() {
        StoreInfoForm info = service.storeInfo();

        assertEquals("Saphikuna", info.getStoreName());
        assertEquals("Tejiendo sueños, vistiendo raíces", info.getTagline());
        assertEquals("", info.getWhatsapp());
        assertEquals("", info.getInstagram());
        assertEquals("", info.getEmail());
        assertEquals("", info.getAddress());
    }

    @Test
    void storeInfoReadsWhatWasSaved() {
        put("storeName", "Saphikuna Tejidos");
        put("storeWhatsapp", "+593 99 123 4567");
        put("storeInstagram", "@saphikuna");
        put("storeAddress", "Quito");

        StoreInfoForm info = service.storeInfo();

        assertEquals("Saphikuna Tejidos", info.getStoreName());
        assertEquals("+593 99 123 4567", info.getWhatsapp());
        assertEquals("@saphikuna", info.getInstagram());
        assertEquals("Quito", info.getAddress());
    }

    @Test
    void aBlankSavedNameOrTaglineFallsBackToTheDefault() {
        put("storeName", "   ");
        put("storeTagline", "");

        StoreInfoForm info = service.storeInfo();

        assertEquals("Saphikuna", info.getStoreName());
        assertEquals("Tejiendo sueños, vistiendo raíces", info.getTagline());
    }

    @Test
    void savingStoreInfoWritesEveryFieldTrimmedAndEmptyForNulls() {
        StoreInfoForm info = new StoreInfoForm();
        info.setStoreName("  Saphikuna  ");
        info.setTagline("Frase");
        info.setWhatsapp(" +593 99 123 4567 ");
        info.setEmail(null);

        service.saveStoreInfo(info);

        verify(repository, times(8)).save(any());
        ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
        verify(repository, times(8)).save(saved.capture());
        Map<String, String> byKey = new HashMap<>();
        saved.getAllValues().forEach(s -> byKey.put(s.getKey(), s.getValue()));
        assertEquals("Saphikuna", byKey.get("storeName"));
        assertEquals("+593 99 123 4567", byKey.get("storeWhatsapp"));
        assertEquals("", byKey.get("storeEmail"));
        assertEquals(List.of("storeAddress", "storeEmail", "storeFacebook", "storeInstagram", "storeName",
                "storePhone", "storeTagline", "storeWhatsapp"), byKey.keySet().stream().sorted().toList());
    }
}
