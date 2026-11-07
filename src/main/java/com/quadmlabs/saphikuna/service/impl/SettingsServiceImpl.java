package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.entity.Setting;
import com.quadmlabs.saphikuna.repository.SettingRepository;
import com.quadmlabs.saphikuna.service.SettingsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SettingsServiceImpl implements SettingsService {

    static final String NEW_PRODUCT_DAYS = "newProductDays";

    static final String STORE_NAME = "storeName";
    static final String STORE_TAGLINE = "storeTagline";
    static final String STORE_WHATSAPP = "storeWhatsapp";
    static final String STORE_PHONE = "storePhone";
    static final String STORE_EMAIL = "storeEmail";
    static final String STORE_INSTAGRAM = "storeInstagram";
    static final String STORE_FACEBOOK = "storeFacebook";
    static final String STORE_ADDRESS = "storeAddress";

    private static final Logger log = LoggerFactory.getLogger(SettingsServiceImpl.class);

    private final SettingRepository repository;
    private final Clock clock;

    /** Lectura en su propia transacción: un fallo aquí no daña la transacción de quien llama. */
    private final TransactionTemplate readInOwnTransaction;

    public SettingsServiceImpl(SettingRepository repository, Clock clock, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.clock = clock;
        this.readInOwnTransaction = new TransactionTemplate(txManager);
        this.readInOwnTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readInOwnTransaction.setReadOnly(true);
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    /**
     * Lee todos los ajustes. Si no se pueden leer (por ejemplo, la tabla aún no existe)
     * devuelve un mapa vacío y las pantallas usan los valores por defecto.
     *
     * No lleva @Transactional: el error se captura FUERA de la transacción interna. Si se
     * capturara dentro, Spring igualmente marcaría la transacción como fallida y la pantalla
     * que llama terminaría con error 500.
     */
    private Map<String, String> readAll() {
        try {
            Map<String, String> all = readInOwnTransaction.execute(status -> {
                Map<String, String> map = new HashMap<>();
                repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
                return map;
            });
            return all == null ? Map.of() : all;
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer los ajustes (¿se ejecutó db/005?): {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public int newProductDays() {
        String value = readAll().get(NEW_PRODUCT_DAYS);
        return value == null ? DEFAULT_NEW_PRODUCT_DAYS : parseDays(value);
    }

    @Override
    public StoreInfoForm storeInfo() {
        Map<String, String> all = readAll();
        StoreInfoForm info = new StoreInfoForm();
        info.setStoreName(valueOrDefault(all, STORE_NAME, DEFAULT_STORE_NAME));
        info.setTagline(valueOrDefault(all, STORE_TAGLINE, DEFAULT_TAGLINE));
        info.setWhatsapp(all.getOrDefault(STORE_WHATSAPP, ""));
        info.setPhone(all.getOrDefault(STORE_PHONE, ""));
        info.setEmail(all.getOrDefault(STORE_EMAIL, ""));
        info.setInstagram(all.getOrDefault(STORE_INSTAGRAM, ""));
        info.setFacebook(all.getOrDefault(STORE_FACEBOOK, ""));
        info.setAddress(all.getOrDefault(STORE_ADDRESS, ""));
        return info;
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void saveNewProductDays(int days) {
        if (days < MIN_NEW_PRODUCT_DAYS || days > MAX_NEW_PRODUCT_DAYS) {
            throw new IllegalArgumentException("Los días deben estar entre "
                    + MIN_NEW_PRODUCT_DAYS + " y " + MAX_NEW_PRODUCT_DAYS);
        }
        upsert(NEW_PRODUCT_DAYS, String.valueOf(days));
    }

    @Override
    @Transactional
    public void saveStoreInfo(StoreInfoForm info) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(STORE_NAME, clean(info.getStoreName()));
        values.put(STORE_TAGLINE, clean(info.getTagline()));
        values.put(STORE_WHATSAPP, clean(info.getWhatsapp()));
        values.put(STORE_PHONE, clean(info.getPhone()));
        values.put(STORE_EMAIL, clean(info.getEmail()));
        values.put(STORE_INSTAGRAM, clean(info.getInstagram()));
        values.put(STORE_FACEBOOK, clean(info.getFacebook()));
        values.put(STORE_ADDRESS, clean(info.getAddress()));

        values.forEach(this::upsert);
    }

    private void upsert(String key, String value) {
        Setting s = repository.findById(key).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(key);
            return created;
        });
        s.setValue(value);
        s.setUpdatedAt(OffsetDateTime.now(clock));
        repository.save(s);
    }

    // ------------------------------------------------------------------

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** Un nombre o frase guardados en blanco se reemplazan por los valores por defecto. */
    private static String valueOrDefault(Map<String, String> all, String key, String fallback) {
        String v = all.get(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static int parseDays(String value) {
        try {
            int days = Integer.parseInt(value.trim());
            return days >= MIN_NEW_PRODUCT_DAYS && days <= MAX_NEW_PRODUCT_DAYS ? days : DEFAULT_NEW_PRODUCT_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_NEW_PRODUCT_DAYS;
        }
    }
}
