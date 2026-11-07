package com.quadmlabs.saphikuna.service.impl;

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

@Service
public class SettingsServiceImpl implements SettingsService {

    static final String NEW_PRODUCT_DAYS = "newProductDays";

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

    /**
     * Si el ajuste no existe, no es válido o no se puede leer (por ejemplo, la tabla aún no
     * existe), devuelve el valor por defecto y la pantalla sigue funcionando.
     *
     * No lleva @Transactional: el error se captura FUERA de la transacción interna. Si se
     * capturara dentro, Spring igualmente marcaría la transacción como fallida y la
     * pantalla que llama terminaría con error 500.
     */
    @Override
    public int newProductDays() {
        try {
            Integer days = readInOwnTransaction.execute(status ->
                    repository.findById(NEW_PRODUCT_DAYS)
                            .map(Setting::getValue)
                            .map(SettingsServiceImpl::parseDays)
                            .orElse(DEFAULT_NEW_PRODUCT_DAYS));
            return days == null ? DEFAULT_NEW_PRODUCT_DAYS : days;
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el ajuste {} (¿se ejecutó db/005?): {}", NEW_PRODUCT_DAYS, e.getMessage());
            return DEFAULT_NEW_PRODUCT_DAYS;
        }
    }

    @Override
    @Transactional
    public void saveNewProductDays(int days) {
        if (days < MIN_NEW_PRODUCT_DAYS || days > MAX_NEW_PRODUCT_DAYS) {
            throw new IllegalArgumentException("Los días deben estar entre "
                    + MIN_NEW_PRODUCT_DAYS + " y " + MAX_NEW_PRODUCT_DAYS);
        }
        Setting s = repository.findById(NEW_PRODUCT_DAYS).orElseGet(() -> {
            Setting created = new Setting();
            created.setKey(NEW_PRODUCT_DAYS);
            return created;
        });
        s.setValue(String.valueOf(days));
        s.setUpdatedAt(OffsetDateTime.now(clock));
        repository.save(s);
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
